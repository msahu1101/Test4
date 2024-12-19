package com.mgm.payments.processing.service.service.processor;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.entity.redis.AuthorizeRedisEntity;
import com.mgm.payments.processing.service.entity.redis.PaymentRedisEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.external.PaymentRouterServiceCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.Amount;
import com.mgm.payments.processing.service.model.AuditData;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.pps.*;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.*;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.auth.AuthorizeRedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.payment.RedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import com.mgm.payments.processing.service.util.SnowFlakeSequenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;
import static com.mgm.payments.processing.service.enums.ApiErrorCode.*;
import static com.mgm.payments.processing.service.util.PaymentProcessingUtil.getCardEntryMode;

@Component
public class AuthorizePaymentProcessor implements PaymentProcessor {

    private final Logger logger = LoggerFactory.getLogger(AuthorizePaymentProcessor.class);
    private final PaymentProcessingRepositoryWrapper repository;
    private final AuthorizeRedisPaymentRepositoryWrapper authorizeRedisRepositoryWrapper;
    private final PaymentRouterServiceCaller routerServiceCaller;
    private final AuditMapper auditMapper;
    private final VoidPaymentProcessor voidPaymentProcessor;
    private final RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper;
    private final SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;
    Tracer tracer;

    @Autowired
    public AuthorizePaymentProcessor(PaymentProcessingRepositoryWrapper repository,
                                     AuthorizeRedisPaymentRepositoryWrapper authorizeRedisRepositoryWrapper,
                                     PaymentRouterServiceCaller routerServiceCaller,
                                     AuditMapper auditMapper,
                                     VoidPaymentProcessor voidPaymentProcessor, RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper, SnowFlakeSequenceGenerator snowFlakeSequenceGenerator, Tracer tracer) {
        this.repository = repository;
        this.authorizeRedisRepositoryWrapper = authorizeRedisRepositoryWrapper;
        this.routerServiceCaller = routerServiceCaller;
        this.auditMapper = auditMapper;
        this.voidPaymentProcessor = voidPaymentProcessor;
        this.redisPaymentRepositoryWrapper = redisPaymentRepositoryWrapper;
        this.snowFlakeSequenceGenerator = snowFlakeSequenceGenerator;
        this.tracer = tracer;
    }

    private String getTraceId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().traceIdString();
        }
        return null;
    }

    private String getSpanId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().spanIdString();
        }
        return null;
    }

    public Mono<PaymentResponse> process(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final PaymentEntity paymentEntity = new PaymentEntity();
        final PaymentResponse paymentResponse = new PaymentResponse();
        LocalDateTime startTime = LocalDateTime.now();
        String maskedRequest = LogMaskingConverter.mask(paymentRequest);
        String mgmId = paymentRequest.getMgmId() != null ? paymentRequest.getMgmId() : "";
        Throwable[] authTransactionException = new Throwable[1];
        try {
            if (logger.isInfoEnabled()) {
                logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, AUTHORIZE_OPERATION, AUTHORIZE_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), maskedRequest);
            }
            publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_AUTHORIZE, "Authorize the given Amount", "", ""}, paymentRequest,
                    maskedRequest, null, null, startTime, headersDTO);

            validateInputRequest(paymentRequest, null, headersDTO);
            populatePaymentEntity(headersDTO, paymentRequest, null, paymentEntity, user);
            createRedisCacheEntry(paymentEntity, headersDTO);
            Mono<PaymentRouterResponse> paymentRouterResponseMono = invokePaymentRouter(paymentEntity,
                    paymentRequest, headersDTO);
            return paymentRouterResponseMono.map(paymentRouterResponse -> {
                        updateRouterResponseInTheDBRecord(paymentEntity, paymentRouterResponse, user, headersDTO, null);
                        mapPaymentRouterResponseToPaymentResponse(paymentEntity, paymentRequest,
                                paymentRouterResponse, paymentResponse, headersDTO);
                        String maskedResponse = LogMaskingConverter.mask(paymentResponse);
                        String status = StatusResult.F.name();
                        String result = StatusResult.F.getResult();
                        String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
                        if (responseCode.equals(RouterResponseCode.A.toString())) {
                            status = StatusResult.S.name();
                            result = StatusResult.S.getResult();
                        }
                        publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_AUTHORIZE, "Authorize the given Amount", result, status}, paymentRequest, maskedRequest,
                                maskedResponse, paymentEntity, startTime, headersDTO);
                        stopWatch.stop();

                logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, AUTHORIZE_OPERATION, AUTHORIZE_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), status, result, stopWatch.getTotalTimeMillis(),
                        maskedResponse);
                        return (paymentResponse);
                    }
            ).onErrorResume(throwable -> {
                authTransactionException[0] = throwable;
                handleException(paymentRequest, headersDTO, throwable, paymentResponse, paymentEntity, startTime);
                return Mono.error(throwable);
            }).doFinally(signalType ->
            {
                updateDBOnFailure(paymentEntity, headersDTO, (Exception) authTransactionException[0]);
                if (SignalType.CANCEL.equals(signalType)) {
                    handleException(paymentRequest, headersDTO, null, paymentResponse, paymentEntity, startTime);
                }

            });
        } catch (Exception e) {
            stopWatch.stop();
            updateDBOnFailure(paymentEntity, headersDTO, e);
            handleException(paymentRequest, headersDTO, e, paymentResponse, paymentEntity, startTime);
            throw e;
        }
    }

    private void handleException(PaymentRequest paymentRequest, HeadersDTO headersDTO, Throwable e,
                                      PaymentResponse paymentResponse, PaymentEntity paymentEntity, LocalDateTime startTime) {
        String mgmErrorCode = null;
        String errorDescription = null;
        String maskedRequest = LogMaskingConverter.mask(paymentRequest);
        String maskedResponse = LogMaskingConverter.mask(paymentResponse);
        String mgmId = paymentRequest.getMgmId() != null ? paymentRequest.getMgmId() : "";
        if (e instanceof PaymentProcessingException) {
            PaymentProcessingException pe = (PaymentProcessingException) e;
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, AUTHORIZE_OPERATION, AUTHORIZE_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", pe.getExceptionResponse());
            mgmErrorCode = pe.getExceptionResponse().getErrorCode();
            errorDescription = pe.getExceptionResponse().getErrorMessage();
        } else {
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, AUTHORIZE_OPERATION, AUTHORIZE_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", e != null ? e.getMessage() : "Exception Occurred");
        }
        publishAuditData(new String[]{mgmErrorCode, errorDescription, AuditTrailConstants.PPS_AUTHORIZE, "Exception while Authorize Call", StatusResult.E.getResult(), StatusResult.E.name()},
                paymentRequest, maskedRequest, maskedResponse, paymentEntity, startTime, headersDTO);
    }

    /**
     * Validates the input Request for authorize transaction
     *
     * @param paymentRequest - input request
     * @param paymentList-   data of payment table
     */
    @Override
    public void validateInputRequest(PaymentRequest paymentRequest, List<PaymentEntity> paymentList, HeadersDTO headersDTO) {
    	/*
    	 * paymentRequest.getPaymentId() - is not required for auth call and only required for void and capture 
    	 */
    	if (paymentList !=null && paymentRequest.getPaymentId() != null && paymentList.stream().anyMatch(entity -> entity.getPaymentId().
                equals(paymentRequest.getPaymentId()) && entity.getTransactionStatus().equals(TransactionStatus.SUCCESS)
                && entity.getTransactionType().equals(TransactionType.AUTHORIZE))) {
            PaymentProcessingUtil.throwException(ApiErrorCode.DUPLICATE_AUTH_REQUEST_CODE.getCode(),
                    ApiErrorCode.DUPLICATE_AUTH_REQUEST_CODE.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }
    	/*
    	 * This check is applicable for auth transaction only
    	 */
        if (isDuplicateRequest(paymentRequest.getClientReferenceNumber(), paymentRequest.getGroupId(), paymentRequest.getAmount(), headersDTO)) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_AUTH_REQUEST_CODE.getCode(),
                    ApiErrorCode.DUPLICATE_AUTH_REQUEST_CODE.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }


    }

    private boolean isDuplicateRequest(String clientReferenceNumber, String groupId, List<Amount> amount, HeadersDTO headersDTO) {
        BigDecimal reqAmount = PaymentProcessingUtil.getAmount(amount);
        Optional<AuthorizeRedisEntity> redisPaymentEntity = authorizeRedisRepositoryWrapper.findById(clientReferenceNumber + groupId, headersDTO);
        return redisPaymentEntity.map(authorizeRedisEntity -> authorizeRedisEntity.getAmount().equals(reqAmount)).orElse(false);
    }

    /**
     * Save the records to the payment table with InProcess transaction Status, before calling router service
     *
     * @param headersDTO  -  headers param
     * @param request     -     input request
     * @param paymentList - data of payment table
     * @param user        - user details
     */
    @Override
    public void populatePaymentEntity(HeadersDTO headersDTO, PaymentRequest request, List<PaymentEntity> paymentList, PaymentEntity paymentEntity, User user) {
        BigDecimal amount = PaymentProcessingUtil.getAmount(request.getAmount());
        // create the record to insert in Database set the status as "IN_PROCESS"
        paymentEntity.setPaymentId(PaymentProcessingUtil.generateUniqueId(snowFlakeSequenceGenerator.nextId()));
        paymentEntity.setClientReferenceNumber(request.getClientReferenceNumber());
        paymentEntity.setGroupId(request.getGroupId());
        paymentEntity.setAmount(amount);
        paymentEntity.setAuthChainId(0);
        paymentEntity.setClerkId("1");
        paymentEntity.setClientId(headersDTO.getClientId());
        paymentEntity.setOrderType(request.getOrderType());
        paymentEntity.setMgmId(request.getMgmId());
        paymentEntity.setMgmToken(request.getPayment().getTenderDetails().getMgmToken());
        paymentEntity.setCardHolderName(request.getPayment().getTenderDetails().getNameOnTender());
        paymentEntity.setTenderType(request.getPayment().getTenderDetails().getTenderType());
        paymentEntity.setLast4DigitsOfTheCard(PaymentProcessingUtil.getLast4Digit(request.getPayment().getTenderDetails().getMaskedCardNumber()));
        paymentEntity.setIssuerType(request.getPayment().getTenderDetails().getIssuerType());
        paymentEntity.setCurrencyCode(request.getPayment().getCurrencyCode());
        paymentEntity.setBillingAddress1(request.getPayment().getBillingAddress().getAddress());
        paymentEntity.setBillingAddress2(request.getPayment().getBillingAddress().getAddress2());
        paymentEntity.setBillingCity(request.getPayment().getBillingAddress().getCity());
        paymentEntity.setBillingState(request.getPayment().getBillingAddress().getState());
        paymentEntity.setBillingZipcode(request.getPayment().getBillingAddress().getPostalCode());
        paymentEntity.setBillingCountry(request.getPayment().getBillingAddress().getCountry());
        paymentEntity.setTransactionType(TransactionType.AUTHORIZE);
        paymentEntity.setTransactionStatus(TransactionStatus.IN_PROCESS);
        paymentEntity.setCardEntryMode(getCardEntryMode(request));
        paymentEntity.setCreatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
        paymentEntity.setUpdatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
        paymentEntity.setCreatedBy(user.getServiceId());
        paymentEntity.setUpdatedBy(user.getServiceId());
        paymentEntity.setMgmCorrelationId(headersDTO.getMgmCorrelationId());
        paymentEntity.setMgmJourneyId(headersDTO.getMgmJourneyId());
        paymentEntity.setMgmTransactionId(headersDTO.getMgmTransactionId());
        paymentEntity.setRequestChannel(headersDTO.getMgmChannel());
        paymentEntity.setSessionId(request.getSessionId());
        paymentEntity.setAuthSubtype("Normal");
    }

    @Override
    public void createRedisCacheEntry(PaymentEntity paymentEntity, HeadersDTO headersDTO) {
        authorizeRedisRepositoryWrapper.save(AuthorizeRedisEntity.builder().id(paymentEntity.getClientReferenceNumber() + paymentEntity.getGroupId())
                .transactionStatus(TransactionStatus.IN_PROCESS)
                .amount(paymentEntity.getAmount()).build(), headersDTO);// insert the record in the table with "IN_PROCESS status  in redis cache
    }

    /**
     * NOT REQUIRED FOR AUTH CALL
     * retrieve client details from session
     *
     * @param paymentRequest- input request
     * @param headersDTO-     headers params
     * @return PaymentSession
     */
    @Override
    public PaymentSession retrieveClientDetailsFromSession(PaymentRequest paymentRequest, HeadersDTO headersDTO) {
        return null;
    }

    /**
     * invokes payment router service
     *
     * @param paymentEntity-  payment table details
     * @param paymentRequest- input request
     * @param headersDTO-     headers param
     * @return PaymentRouterResponse
     */

    @Override
    public Mono<PaymentRouterResponse> invokePaymentRouter(PaymentEntity paymentEntity, PaymentRequest paymentRequest,
                                                                           HeadersDTO headersDTO) {
        LocalDateTime startTime = LocalDateTime.now();
        PaymentRouterRequest paymentRouterRequest = PaymentRouterRequest.builder()
                .clientReferenceNumber(paymentRequest.getClientReferenceNumber())
                .mgmId(paymentRequest.getMgmId())
                .routerFunction(RouterFunction.AUTHORIZE)
                .sessionId(paymentRequest.getSessionId())
                .gatewayId(paymentEntity.getGatewayId()) // will be null first time
                .amount(paymentRequest.getAmount())
                .payment(paymentRequest.getPayment())
                .hotelData(paymentRequest.getHotelData())
                .merchantReferenceCode(paymentEntity.getPaymentId())
                .additionalAttributes(paymentRequest.getAdditionalAttributes())
                .build();
        String maskedRouterRequest = LogMaskingConverter.mask(paymentRouterRequest);
        publishAuditData(new String[]{"", "", "PPS_TO_PR_AUTHORIZE", "PPS to Payment Router - Authorize the given Amount", "", ""}, paymentRequest, maskedRouterRequest,
                null, paymentEntity, startTime, headersDTO);
        Mono<ResponseEntity<PaymentRouterResponse>> paymentRouterResponseMono =
                routerServiceCaller.invokeRouter(paymentRouterRequest, headersDTO, paymentEntity.getPaymentId());
        return paymentRouterResponseMono.map(paymentRouterResponseResponseEntity ->
        {
            PaymentRouterResponse paymentRouterResponse = paymentRouterResponseResponseEntity.getBody();
            String maskedRouterResponse = LogMaskingConverter.mask(paymentRouterResponse);
            String result = StatusResult.F.getResult();
            String status = StatusResult.F.name();
            assert paymentRouterResponse != null;
            String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if (responseCode.equals(RouterResponseCode.A.toString())) {
                result = StatusResult.S.getResult();
                status = StatusResult.S.name();
            }
            publishAuditData(new String[]{"", "", "PPS_TO_PR_AUTHORIZE", "PPS to Payment Router - Authorize the given Amount", result, status}, paymentRequest, maskedRouterRequest,
                    maskedRouterResponse, paymentEntity, startTime, headersDTO);
            return paymentRouterResponse;
        });
    }


    /**
     * Updates the database with prResponse after router call
     *
     * @param payment    -    database record of the auth transaction
     * @param prResponse - response from router service
     * @param user   - user details
     */
    @Override
    public void updateRouterResponseInTheDBRecord(PaymentEntity payment, PaymentRouterResponse prResponse, User user, HeadersDTO headersDTO, List<PaymentEntity> paymentList) {
        Optional<RouterResult> routerResultOp = prResponse.getResults().stream().findFirst();
        try {
            RouterGatewayResult gatewayResult = routerResultOp.map(RouterResult::getGatewayResult).orElse(null);
            assert gatewayResult != null;
            payment.setGatewayChainId(gatewayResult.getTransaction().getGatewayChainId());
            payment.setGatewayId(gatewayResult.getCard().getGatewayId());
            payment.setCvvResponseCode(gatewayResult.getCard().getSecurityCodeResult());
            payment.setPaymentAuthId(gatewayResult.getTransaction().getAuthorizationCode());
            BigDecimal amount = PaymentProcessingUtil.getAmount(gatewayResult.getAmount());
            TransactionStatus transactionStatus = TransactionStatus.valueOf(RouterResponseCode.valueOf(gatewayResult.getTransaction().getResponseCode()).getStatus());
            payment.setTransactionStatus(transactionStatus);
            payment.setAuthorizedAmount(amount != null && !TransactionStatus.FAILURE.equals(transactionStatus) ? amount : new BigDecimal(0));
            RouterTransactionResponse transactionResponse = gatewayResult.getTransaction();
            payment.setGatewayTransactionStatusCode(transactionResponse.getGatewayResponse().getReasonCode());
            payment.setGatewayTransactionStatusReason(transactionResponse.getGatewayResponse().getReasonDescription());
            payment.setGatewayResponseCode(transactionResponse.getResponseCode());
            payment.setGatewayRrn(transactionResponse.getRetrievalReference());
            payment.setDeferredAuth(transactionResponse.getDeferredAuth());
            payment.setPaymentAuthSource(transactionResponse.getAuthSource());
            payment.setAvsResponseCode(transactionResponse.getAvsResult());
            payment.setUpdatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
            payment.setUpdatedBy(user.getServiceId());
            repository.save(payment, headersDTO);
        }
        catch (Exception e){
            RouterGatewayResult gatewayResult = routerResultOp.map(RouterResult::getGatewayResult).orElse(null);
            assert gatewayResult != null;
            TransactionStatus transactionStatus = TransactionStatus.valueOf(RouterResponseCode.valueOf(gatewayResult.getTransaction().getResponseCode()).getStatus());
            if (transactionStatus.equals(TransactionStatus.SUCCESS)) {
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, AUTHORIZE_OPERATION, AUTHORIZE_CLASS_NAME, PPS_AUTH_DB_ENTRY_EXCEPTION,
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.S.name(), StatusResult.S.getResult(), "",
                        e.getMessage());
                PaymentRequest paymentRequest = PaymentRequest.builder().clientReferenceNumber(payment.getClientReferenceNumber())
                        .mgmId(payment.getMgmId()).sessionId(payment.getSessionId())
                        .build();

                voidPaymentProcessor.invokePaymentRouter(payment, paymentRequest, new HeadersDTO(PPS_AUTH_DB_ENTRY_EXCEPTION,
                        payment.getMgmJourneyId(), payment.getMgmCorrelationId(), payment.getMgmTransactionId(),
                        payment.getRequestChannel(), null, payment.getClientId(), "user-agent"));
                throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                        .dateTime(ZonedDateTime.now()).errorCode(SUCCESS_AUTH_DB_ENTRY_EXCEPTION.getCode())
                        .errorMessage(SUCCESS_AUTH_DB_ENTRY_EXCEPTION.getDescription())
                        .developerMessage("Exception while updating DB with the response from Router").build(),
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, AUTHORIZE_OPERATION, AUTHORIZE_CLASS_NAME, PPS_AUTH_DB_ENTRY_EXCEPTION,
                    payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                    payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.F.name(), StatusResult.F.getResult(), "",
                    e.getMessage());
            throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                    .dateTime(ZonedDateTime.now()).errorCode(FAILURE_AUTH_DB_ENTRY_EXCEPTION.getCode())
                    .errorMessage(FAILURE_AUTH_DB_ENTRY_EXCEPTION.getDescription())
                    .developerMessage("Exception while updating DB with the response from Router").build(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        finally {
            authorizeRedisRepositoryWrapper.deleteById(payment.getClientReferenceNumber() + payment.getGroupId(), headersDTO);
        }

    }

    /**
     * maps the payment router response to paymentResponse and returns the response
     *
     * @param payment                - database record
     * @param paymentRequest-        inputRequest
     * @param paymentRouterResponse- payment router response
     *
     */
    @Override
    public void mapPaymentRouterResponseToPaymentResponse(PaymentEntity payment, PaymentRequest paymentRequest,
                                                          PaymentRouterResponse paymentRouterResponse,
                                                          PaymentResponse paymentResponse, HeadersDTO headersDTO) {


        try {
            List<RouterResult> routerResultList = paymentRouterResponse.getResults();
            List<Result> resultList = new ArrayList<>();
            for (RouterResult routerResult : routerResultList) {
                RouterGatewayResult routerGatewayResult = routerResult.getGatewayResult();
                RouterCardResponse routerCardResponse = routerGatewayResult.getCard();
                RouterTransactionResponse transactionResponse = routerGatewayResult.getTransaction();
                List<Amount> amount = new ArrayList<>();
                amount.add(Amount.builder().name(TOTAL_AMOUNT).
                        value(payment.getAmount()).build()); // input Req amount
                amount.add(Amount.builder().name(AUTHORIZED_AMOUNT).
                        value(payment.getAuthorizedAmount()).build()); // authorized amount
                resultList.add(Result.builder().gatewayResult(GatewayResult.builder()
                        .type(TransactionType.AUTHORIZE)
                        .clientReferenceNumber(routerGatewayResult.getClientReferenceNumber())
                        .groupId(payment.getGroupId())
                        .mgmId(routerGatewayResult.getMgmId()).amount(amount)
                        .card(Card.builder().maskedCardNumber(routerGatewayResult.getCard().getTenderDisplay())
                                .mgmToken(paymentRequest.getPayment().getTenderDetails().getMgmToken())
                                .issuerType(routerCardResponse.getIssuerType())
                                .gatewayId(routerCardResponse.getGatewayId())
                                .securityCodeResult(routerCardResponse.getSecurityCodeResult())
                                .securityCodeValid(routerCardResponse.getSecurityCodeValid())
                                .build())
                        .transactionStatus(payment.getTransactionStatus().toString())
                        .transactionCode(payment.getTransactionStatus().getStatusCode())
                        .dateTime(routerGatewayResult.getDateTime())
                        .transaction(Transaction.builder().paymentId(payment.getPaymentId())
                                .authorizationCode(payment.getPaymentAuthId())
                                .gatewayResponse(transactionResponse.getGatewayResponse())
                                .avsResult(transactionResponse.getAvsResult())
                                .avsValid(transactionResponse.getAvsValid())
                                .responseCode(transactionResponse.getResponseCode()).build())
                        .build()).build());

            }
            paymentResponse.setStatusCode(SUCCESS_RESPONSE_STATUS_CODE.toString());
            paymentResponse.setStatusDesc(SUCCESS_RESPONSE_STATUS);
            paymentResponse.setResults(resultList);
            PaymentRedisEntity redisPaymentEntity = PaymentProcessingUtil.mapToRedisEntity(payment);
            redisPaymentRepositoryWrapper.save(redisPaymentEntity, headersDTO);
        } catch (Exception e) {
            throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                    .dateTime(ZonedDateTime.now()).errorCode(PAYMENT_ROUTER_RESPONSE_PROCESSING_EXCEPTION.getCode()).errorMessage(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getDescription())
                    .developerMessage("Exception while updating PPS response with the response from Router").build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    /**
     * Updates the transaction status to FAILURE, in case of exception while calling router-service
     *
     * @param payment - database record of the transaction
     * @param e - exception
     */
    @Override
    public void updateDBOnFailure(PaymentEntity payment, HeadersDTO headersDTO, Exception e) {
        if (payment != null && (TransactionStatus.IN_PROCESS.equals(payment.getTransactionStatus()))) {
            authorizeRedisRepositoryWrapper.deleteById(payment.getClientReferenceNumber() + payment.getGroupId(), headersDTO);
            PaymentProcessingUtil.getFailureEntity(payment, e);
            repository.save(payment, headersDTO);
        }
    }

    private void publishAuditData(String[] errorEventData, PaymentRequest paymentRequest, Object requestPayload, Object responsePayload,
                                  PaymentEntity paymentEntity, LocalDateTime startTime, HeadersDTO headersDTO) {
        String errorCode = errorEventData[0];
        String errorDescription = errorEventData[1];
        String eventName = errorEventData[2];
        String eventDescription = errorEventData[3];
        String result = errorEventData[4];
        String status = errorEventData[5];
        String gatewayChainId = paymentEntity != null ? paymentEntity.getGatewayChainId() : null;

        AuditData auditData = PaymentProcessingUtil.buildAuditData(eventName, eventDescription, errorCode, errorDescription,
                requestPayload, responsePayload, paymentEntity);
        auditData.setStartTimeTS(startTime);
        auditData.setResult(result);
        auditData.setStatus(status);
        auditMapper.createAndPublishAuditTrailForRequestResponse(paymentRequest.getClientReferenceNumber(), gatewayChainId,
                paymentRequest.getSessionId(), paymentRequest.getMgmId(), "", headersDTO, auditData);
    }

}
