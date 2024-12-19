package com.mgm.payments.processing.service.service.processor;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.entity.redis.RefundRedisEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.external.ClientConfigurationServiceCaller;
import com.mgm.payments.processing.service.external.PaymentRouterServiceCaller;
import com.mgm.payments.processing.service.external.SessionServiceCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfigPayload;
import com.mgm.payments.processing.service.model.payload.pps.*;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.*;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.refund.RefundRedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import com.mgm.payments.processing.service.util.SnowFlakeSequenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;
import static com.mgm.payments.processing.service.enums.ApiErrorCode.PAYMENT_ROUTER_RESPONSE_PROCESSING_EXCEPTION;
import static com.mgm.payments.processing.service.enums.ApiErrorCode.PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION;
import static com.mgm.payments.processing.service.util.PaymentProcessingUtil.*;

@Component
public class RefundPaymentProcessor implements PaymentProcessor {
    private final Logger logger = LoggerFactory.getLogger(RefundPaymentProcessor.class);


    PaymentProcessingRepositoryWrapper repository;
    private final PaymentRouterServiceCaller routerServiceCaller;
    private final AuditMapper auditMapper;
    private final RefundRedisPaymentRepositoryWrapper refundRedisPaymentRepositoryWrapper;
    private final ClientConfigurationServiceCaller clientConfigurationServiceCaller;
    Tracer tracer;
    private final SessionServiceCaller sessionServiceCaller;
    private final SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;


    @Autowired
    public RefundPaymentProcessor(PaymentProcessingRepositoryWrapper repository, PaymentRouterServiceCaller routerServiceCaller,
                                  AuditMapper auditMapper, RefundRedisPaymentRepositoryWrapper refundRedisPaymentRepositoryWrapper,
                                  ClientConfigurationServiceCaller clientConfigurationServiceCaller, Tracer tracer, SessionServiceCaller sessionServiceCaller, SnowFlakeSequenceGenerator snowFlakeSequenceGenerator) {
        this.repository = repository;
        this.routerServiceCaller = routerServiceCaller;
        this.auditMapper = auditMapper;
        this.refundRedisPaymentRepositoryWrapper = refundRedisPaymentRepositoryWrapper;
        this.clientConfigurationServiceCaller = clientConfigurationServiceCaller;
        this.tracer = tracer;
        this.sessionServiceCaller = sessionServiceCaller;
        this.snowFlakeSequenceGenerator = snowFlakeSequenceGenerator;
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

    @Override
    public Mono<PaymentResponse> process(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final ClientConfigPayload clientConfig;
        final boolean isAdhoc;
        final PaymentResponse paymentResponse = new PaymentResponse();
        LocalDateTime startTime = LocalDateTime.now();
        final PaymentEntity paymentEntity = new PaymentEntity();
        String maskedRequest = LogMaskingConverter.mask(paymentRequest);
        String mgmId = paymentRequest.getMgmId() != null ? paymentRequest.getMgmId() : "";
        Throwable[] refundTransactionException = new Throwable[1];
        if (logger.isInfoEnabled()) {
            logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), maskedRequest);
        }
        try {
            publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_REFUND, "Refund the given Amount", "", ""}, paymentRequest, maskedRequest,
                    null, paymentEntity, startTime, headersDTO);
            validateDuplicateRequestFromCache(paymentRequest.getClientReferenceNumber(), paymentRequest.getAmount(), headersDTO);
            // The below commented code is to decide adhoc-refund from DB existing records. As of now, we are checking the adhoc-refund flag from client-config using clientId.
//            List<PaymentEntity> paymentList = repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO);
//            List<PaymentEntity> captureEntityList = paymentList.stream().filter(data ->
//                    data.getTransactionType() == TransactionType.CAPTURE && data.getTransactionStatus() == TransactionStatus.SUCCESS).collect(Collectors.toList());
//            String merchantReferenceCode = !captureEntityList.isEmpty() ? captureEntityList.get(0).getReferenceId() : null;
//            isAdhoc = captureEntityList.isEmpty();
//            paymentRequest.setPaymentId(merchantReferenceCode);
            populatePaymentEntity(headersDTO, paymentRequest,null, paymentEntity, user);
            createRedisCacheEntry(paymentEntity, headersDTO);
            if(!PAM_SERVICE.equals(headersDTO.getMgmSource())) {
                PaymentProcessingUtil.validateRefundRequest(paymentRequest);
                PaymentSession paymentSession = retrieveClientDetailsFromSession(paymentRequest, headersDTO);
                PaymentProcessingUtil.mapDerivedClientId(paymentSession, headersDTO, paymentRequest);
            }
            clientConfig = clientConfigurationServiceCaller.getClientConfig(headersDTO);
            headersDTO.setClientId(clientConfig.getClientId());
            paymentEntity.setClientId(clientConfig.getClientId());
            validateDuplicateRequestFromDB(paymentRequest, headersDTO);
            isAdhoc = PaymentProcessingUtil.isAdhocRefundAllowed(clientConfig, "PaymentConfigs", "adhocRefund");
            if (!isAdhoc) {
                logger.info(PPS_REQUEST_INFO_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                        "Refund is Non-Adhoc for clientReferenceNumber: "+ paymentRequest.getClientReferenceNumber());
                List<PaymentEntity> paymentList = repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO);
                validateInputRequest(paymentRequest, paymentList, headersDTO);
            } else{
                logger.info(PPS_REQUEST_INFO_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                        "Refund is Adhoc for clientReferenceNumber: "+ paymentRequest.getClientReferenceNumber());
            }


            Mono<PaymentRouterResponse> prResponseMono = invokePaymentRouter(paymentEntity,
                    paymentRequest, headersDTO);
            return prResponseMono.map(paymentRouterResponse ->
            {
                updateRouterResponseInTheDBRecord(paymentEntity, paymentRouterResponse, user, headersDTO, null);
                mapPaymentRouterResponseToPaymentResponse(paymentEntity, paymentRequest,
                        paymentRouterResponse, paymentResponse, headersDTO);
                String maskedResponse = LogMaskingConverter.mask(paymentResponse);
                String status = StatusResult.F.name();
                String result = StatusResult.F.getResult();
                String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
                if(responseCode.equals(RouterResponseCode.A.toString())){
                    status = StatusResult.S.name();
                    result = StatusResult.S.getResult();
                }
                publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_REFUND, "Refund the given Amount", result, status}, paymentRequest, maskedRequest,
                        maskedResponse, paymentEntity, startTime, headersDTO);
                stopWatch.stop();
                logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), status, result, stopWatch.getTotalTimeMillis(),
                        maskedResponse);
                return paymentResponse;
            }).onErrorResume(throwable -> {
                refundTransactionException[0] = throwable;
                handleException(paymentRequest, headersDTO, throwable, paymentResponse, paymentEntity, startTime);
                return Mono.error(throwable);
            }).doFinally(signalType ->
            {
                updateDBOnFailure(paymentEntity, headersDTO, (Exception) refundTransactionException[0]);
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

    private void handleException(PaymentRequest paymentRequest, HeadersDTO headersDTO, Throwable e, PaymentResponse paymentResponse, PaymentEntity paymentEntity, LocalDateTime startTime) {
        String mgmErrorCode = null;
        String errorDescription = null;
        String maskedRequest = LogMaskingConverter.mask(paymentRequest);
        String maskedResponse = LogMaskingConverter.mask(paymentResponse);
        String mgmId = paymentRequest.getMgmId() != null ? paymentRequest.getMgmId() : "";
        if (e instanceof PaymentProcessingException) {
            PaymentProcessingException pe = (PaymentProcessingException) e;
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", pe.getExceptionResponse());
            mgmErrorCode = pe.getExceptionResponse().getErrorCode();
            errorDescription = pe.getExceptionResponse().getErrorMessage();
        }else {
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", e != null ? e.getMessage() : "Exception Occurred");
        }
        publishAuditData(new String[]{mgmErrorCode, errorDescription, AuditTrailConstants.PPS_REFUND, "Exception while Refund of the given Amount", StatusResult.E.getResult(), StatusResult.E.name()},
                paymentRequest, maskedRequest, maskedResponse, paymentEntity, startTime, headersDTO);
    }


    /**
     * @param paymentRequest    - input request
     * @param paymentList    - list of payment entities
     */
    @Override
    public void validateInputRequest(PaymentRequest paymentRequest, List<PaymentEntity> paymentList, HeadersDTO headersDTO) {
        List<PaymentEntity> captureList = paymentList.stream()
                    .filter(data -> data.getTransactionType().equals(TransactionType.CAPTURE)
                            && (data.getTransactionStatus().equals(TransactionStatus.SUCCESS)
                            )).collect(Collectors.toList());
        if(captureList.isEmpty()){
            throwException(
                    ApiErrorCode.INVALID_REFUND_ERROR.getCode(),
                    ApiErrorCode.INVALID_REFUND_ERROR.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }
        BigDecimal refundAmount = PaymentProcessingUtil.getAmount(paymentRequest.getAmount());
        BigDecimal authAmount = captureList.stream()
                    .map(PaymentEntity :: getAuthorizedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refAmount = paymentList.stream()
                    .filter(data -> data.getTransactionType().equals(TransactionType.REFUND)
                            && data.getTransactionStatus().equals(TransactionStatus.SUCCESS))
                .map(PaymentEntity :: getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (refundAmount.compareTo(authAmount.subtract(refAmount)) > 0) {
                throwException(
                        ApiErrorCode.REFUND_LIMIT_EXCEEDS.getCode(),
                        ApiErrorCode.REFUND_LIMIT_EXCEEDS.getDescription(), HttpStatus.PRECONDITION_FAILED);
            }

    }

    /**
     * retrieve client details from session
     *
     * @param paymentRequest- input request
     * @param headersDTO-     headers params
     * @return PaymentSession
     */
    @Override
    public PaymentSession retrieveClientDetailsFromSession(PaymentRequest paymentRequest, HeadersDTO headersDTO) {
        publishAuditData(new String[]{"", "", "PPS_TO_SESSION", "PPS to Payment Session - Retrieve Session", "", ""}, paymentRequest, paymentRequest.getSessionId(),
                null, null, LocalDateTime.now(), headersDTO);
        PaymentSession paymentSession = sessionServiceCaller.retrieveSession(headersDTO, paymentRequest.getSessionId());
        String maskedResponse = LogMaskingConverter.mask(paymentSession);
        publishAuditData(new String[]{"", "", "PPS_TO_SESSION", "PPS to Payment Session - Retrieve Session", StatusResult.S.getResult(), StatusResult.S.name()}, paymentRequest, paymentRequest.getSessionId(),
                maskedResponse, null, LocalDateTime.now(), headersDTO);
        return paymentSession;
    }

    private void validateDuplicateRequestFromCache(String clientReferenceNumber, List<Amount> amount, HeadersDTO headersDTO) {
        Optional<RefundRedisEntity> refundRedisEntity = refundRedisPaymentRepositoryWrapper.findById(clientReferenceNumber, headersDTO);
        if(refundRedisEntity.isPresent()) {
            if (refundRedisEntity.get().getAmount()
                    .equals(PaymentProcessingUtil.getAmount(amount))) {
                PaymentProcessingUtil.throwException(
                        ApiErrorCode.DUPLICATE_REFUND_MESSAGE.getCode(),
                        ApiErrorCode.DUPLICATE_REFUND_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);
            }
        }
    }

    /**
     * Validates that a refund with the same Client Id, Client Reference Id, Amount and Transaction Date has not been
     * processed already
     * @param paymentRequest
     * @param headersDTO
     */
    protected void validateDuplicateRequestFromDB(PaymentRequest paymentRequest, HeadersDTO headersDTO) {
        logger.info("Validating duplicate request from DB for clientReferenceNumber: {}", sanitize(paymentRequest.getClientReferenceNumber()));
        List<PaymentEntity> paymentEntities = repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO);
        if(!CollectionUtils.isEmpty(paymentEntities) && paymentEntities.stream().anyMatch(paymentEntity ->
                        !ObjectUtils.isEmpty(paymentEntity.getTransactionType())
                                && !ObjectUtils.isEmpty(paymentEntity.getAmount())
                                && !ObjectUtils.isEmpty(paymentEntity.getCreatedTimestamp())
                                && !ObjectUtils.isEmpty(paymentEntity.getRequestChannel())
                                && !ObjectUtils.isEmpty(paymentRequest.getPayment())
                                && !ObjectUtils.isEmpty(paymentRequest.getPayment().getTenderDetails())
                                && !ObjectUtils.isEmpty(paymentRequest.getPayment().getTenderDetails().getMgmToken())
                                && StringUtils.hasText(paymentEntity.getRequestChannel())
                                && StringUtils.hasText(paymentEntity.getLast4DigitsOfTheCard())
                                //checking for the refund transaction with the same amount, created date, request channel and last 4 digits of the card
                                && paymentEntity.getTransactionType().equals(TransactionType.REFUND)
                                && paymentEntity.getAmount().equals(PaymentProcessingUtil.getAmount(paymentRequest.getAmount()))
                                && PaymentProcessingUtil.compareDate(paymentEntity.getCreatedTimestamp())
                                && paymentEntity.getRequestChannel().equals(headersDTO.getMgmChannel())
                                //getting last 4 digits of the card from mgmToken
                                && paymentEntity.getLast4DigitsOfTheCard().equals(paymentRequest.getPayment().getTenderDetails().getMgmToken().substring(
                                        paymentRequest.getPayment().getTenderDetails().getMgmToken().length() - 4)))) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_REFUND_MESSAGE.getCode(),
                    ApiErrorCode.DUPLICATE_REFUND_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }
        logger.info("Duplicate request validation from DB successful for clientReferenceNumber: {}", sanitize(paymentRequest.getClientReferenceNumber()));
    }

    @Override
    public void populatePaymentEntity(HeadersDTO headersDTO, PaymentRequest request, List<PaymentEntity> paymentEntityList, PaymentEntity paymentEntity, User user) {

        BigDecimal amount = PaymentProcessingUtil.getAmount(request.getAmount());
        TenderDetails tenderDetails = request.getPayment().getTenderDetails();
        paymentEntity.setPaymentId(PaymentProcessingUtil.generateUniqueId(snowFlakeSequenceGenerator.nextId()));
        paymentEntity.setClientReferenceNumber(request.getClientReferenceNumber());
        paymentEntity.setGroupId(request.getGroupId());
        paymentEntity.setAmount(amount);
        paymentEntity.setAuthChainId(0);
        paymentEntity.setClerkId("1");
        paymentEntity.setClientId(headersDTO.getClientId());
        paymentEntity.setOrderType(request.getOrderType());
        paymentEntity.setMgmId(request.getMgmId());
        paymentEntity.setMgmToken(tenderDetails.getMgmToken());
        paymentEntity.setCardHolderName(tenderDetails.getNameOnTender());
        paymentEntity.setTenderType(tenderDetails.getTenderType());
        paymentEntity.setLast4DigitsOfTheCard(PaymentProcessingUtil.getLast4Digit(tenderDetails.getMaskedCardNumber()));
        paymentEntity.setIssuerType(tenderDetails.getIssuerType());
        paymentEntity.setCurrencyCode(request.getPayment().getCurrencyCode());
        BillingAddress billingAddress = request.getPayment().getBillingAddress();
        if(billingAddress != null) {
            paymentEntity.setBillingAddress1(billingAddress.getAddress());
            paymentEntity.setBillingAddress2(billingAddress.getAddress2());
            paymentEntity.setBillingCity(billingAddress.getCity());
            paymentEntity.setBillingState(billingAddress.getState());
            paymentEntity.setBillingZipcode(billingAddress.getPostalCode());
            paymentEntity.setBillingCountry(billingAddress.getCountry());
        }
        paymentEntity.setTransactionType(TransactionType.REFUND);
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


    public void createRedisCacheEntry(PaymentEntity paymentEntity, HeadersDTO headersDTO) {
        refundRedisPaymentRepositoryWrapper.save(RefundRedisEntity.builder().id(paymentEntity.getClientReferenceNumber())
                .amount(paymentEntity.getAmount()).transactionStatus(TransactionStatus.IN_PROCESS).build(), headersDTO);
    }


    public Mono<PaymentRouterResponse> invokePaymentRouter(PaymentEntity paymentEntity, PaymentRequest paymentRequest,
                                                                           HeadersDTO headersDTO) {
        LocalDateTime startTime = LocalDateTime.now();

        // invoke payment router
        HotelData hotelData = paymentRequest.getHotelData();
        if(hotelData != null){
            hotelData.setFolioNumber(paymentRequest.getClientReferenceNumber());
        }
        PaymentRouterRequest paymentRouterRequest = PaymentRouterRequest.builder()
                .routerFunction(RouterFunction.REFUND)
                .amount(paymentRequest.getAmount())
                .clientReferenceNumber(paymentRequest.getClientReferenceNumber())
                .sessionId(paymentRequest.getSessionId())
                .mgmId(paymentEntity.getMgmId())
                .gatewayChainId(null)
                .gatewayId(paymentEntity.getGatewayId())
                .payment(paymentRequest.getPayment())
                .hotelData(hotelData)
                .merchantReferenceCode(paymentRequest.getPaymentId())
                .additionalAttributes(paymentRequest.getAdditionalAttributes())
                .build();
        String maskedRequest = LogMaskingConverter.mask(paymentRouterRequest);
        publishAuditData(new String[]{"", "", "PPS_TO_PR_REFUND", "PPS to Payment Router - Refund the given Amount", "", ""}, paymentRequest, maskedRequest,
                null, paymentEntity, startTime, headersDTO);
        Mono<ResponseEntity<PaymentRouterResponse>> paymentRouterResponseMono = routerServiceCaller.invokeRouter(paymentRouterRequest, headersDTO, paymentEntity.getPaymentId());
        return  paymentRouterResponseMono.map(paymentRouterResponseResponseEntity ->
        {
            PaymentRouterResponse paymentRouterResponse = paymentRouterResponseResponseEntity.getBody();
            String maskedResponse = LogMaskingConverter.mask(paymentRouterResponse);
            String result = StatusResult.F.getResult();
            String status = StatusResult.F.name();
            assert paymentRouterResponse != null;
            String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                result = StatusResult.S.getResult();
                status = StatusResult.S.name();
            }
            publishAuditData(new String[]{"", "", "PPS_TO_PR_REFUND", "PPS to Payment Router - Refund the given Amount", result, status}, paymentRequest, maskedRequest,
                    maskedResponse, paymentEntity, startTime, headersDTO);
            return paymentRouterResponse;
        });

    }

    /**
     * Update DB with the router response and status to SUCCESS
     *
     * @param payment    -         db record
     * @param prResponse - router service response
     * @param user       - user object
     */
    public void updateRouterResponseInTheDBRecord(PaymentEntity payment,
                                                  PaymentRouterResponse prResponse, User user, HeadersDTO headersDTO, List<PaymentEntity> paymentList) {
        try {
            Optional<RouterResult> optionalRouterResult = prResponse.getResults().stream().findFirst();
            RouterGatewayResult gatewayResult = optionalRouterResult.map(RouterResult::getGatewayResult).orElse(null);
            assert gatewayResult != null;
            RouterTransactionResponse transactionResponse = gatewayResult.getTransaction();
            payment.setGatewayChainId(transactionResponse.getGatewayChainId());
            payment.setGatewayId(gatewayResult.getCard().getGatewayId());
            payment.setCvvResponseCode(gatewayResult.getCard().getSecurityCodeResult());
            payment.setPaymentAuthId(StringUtils.hasText(transactionResponse.getAuthorizationCode()) ? transactionResponse.getAuthorizationCode() : payment.getPaymentId());
            payment.setLast4DigitsOfTheCard(PaymentProcessingUtil.getLast4Digit(gatewayResult.getCard().getTenderDisplay()));
            TransactionStatus transactionStatus = TransactionStatus.valueOf(
                    RouterResponseCode.valueOf(transactionResponse.getResponseCode()).getStatus());
            payment.setTransactionStatus(transactionStatus);
            BigDecimal amount =PaymentProcessingUtil.getAmount(gatewayResult.getAmount());
            payment.setAuthorizedAmount(amount!=null && !TransactionStatus.FAILURE.equals(transactionStatus) ? amount:new BigDecimal(0));
            payment.setGatewayTransactionStatusCode(transactionResponse.getGatewayResponse().getReasonCode());
            payment.setGatewayTransactionStatusReason(transactionResponse.getGatewayResponse().getReasonDescription());
            payment.setGatewayResponseCode(transactionResponse.getResponseCode());
            payment.setGatewayRrn(transactionResponse.getRetrievalReference());
            payment.setDeferredAuth(transactionResponse.getDeferredAuth());

            payment.setPaymentAuthSource(transactionResponse.getAuthSource());
            payment.setAvsResponseCode(transactionResponse.getAvsResult());
            payment.setUpdatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
            payment.setUpdatedBy(user.getServiceId());
            saveResponseToDB(payment, prResponse, transactionStatus, headersDTO);
            refundRedisPaymentRepositoryWrapper.deleteById(payment.getClientReferenceNumber(), headersDTO);
        } catch (Exception e) {
            throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                    .dateTime(ZonedDateTime.now()).errorCode(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getCode()).errorMessage(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getDescription())
                    .developerMessage("Exception while updating DB with the response from Router").build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void saveResponseToDB(PaymentEntity payment, PaymentRouterResponse prResponse, TransactionStatus transactionStatus, HeadersDTO headersDTO) {
        try {
            repository.save(payment, headersDTO);
        }catch(Exception e) {
            String maskedResponse = LogMaskingConverter.mask(prResponse);
            if(transactionStatus.equals(TransactionStatus.SUCCESS)){
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, "SUCCESS_REFUND_DB_EXCEPTION",
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.S.name(), StatusResult.S.getResult(), "",
                        e.getMessage());
                publishAuditData(new String[]{"", "", "SUCCESS_REFUND_DB_EXCEPTION", "Exception while updating the DB with the " +
                        "successful refund response", StatusResult.S.getResult(), StatusResult.S.name()}, PaymentRequest.builder().clientReferenceNumber(payment
                                .getClientReferenceNumber()).sessionId(payment.getSessionId()).mgmId(payment.getMgmId())
                        .build(), null, maskedResponse, payment, LocalDateTime.now(), new HeadersDTO(
                        "PPS_REFUND_DB_ENTRY_EXCEPTION", payment.getMgmJourneyId(),
                        payment.getMgmCorrelationId(), payment.getMgmTransactionId(), payment.getRequestChannel(),
                        null, payment.getClientId(), "user-agent")
                );
            }
            else {
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, REFUND_OPERATION, REFUND_CLASS_NAME, "FAILED_REFUND_DB_EXCEPTION",
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.F.name(), StatusResult.F.getResult(), "",
                        e.getMessage());
                publishAuditData(new String[]{"", "", "FAILED_REFUND_DB_EXCEPTION", "Exception while updating the DB with the " +
                        "failed refund response", StatusResult.F.getResult(), StatusResult.F.name()}, PaymentRequest.builder().clientReferenceNumber(payment
                                .getClientReferenceNumber()).sessionId(payment.getSessionId()).mgmId(payment.getMgmId())
                        .build(), null, maskedResponse, payment, LocalDateTime.now(), new HeadersDTO(
                        "PPS_REFUND_DB_ENTRY_EXCEPTION", payment.getMgmJourneyId(),
                        payment.getMgmCorrelationId(), payment.getMgmTransactionId(), payment.getRequestChannel(),
                        null, payment.getClientId(), "user-agent")
                );
            }
        }
    }

    /**
     * Maps router response to paymentResponse and returns
     *
     * @param payment-        db record
     * @param paymentRequest- input request
     * @param prResponse-     payment router response
     */


    @Override
    public void mapPaymentRouterResponseToPaymentResponse(PaymentEntity payment,
                                                                     PaymentRequest paymentRequest,
                                                                     PaymentRouterResponse prResponse,
                                                                     PaymentResponse paymentResponse, HeadersDTO headersDTO

    ) {
        try {
            List<RouterResult> routerResultList = prResponse.getResults();
            List<Result> resultList = new ArrayList<>();
            for (RouterResult routerResult : routerResultList) {

                RouterGatewayResult routerGatewayResult = routerResult.getGatewayResult();
                RouterCardResponse routerCardResponse = routerGatewayResult.getCard();
                RouterTransactionResponse transactionResponse = routerGatewayResult.getTransaction();
                List<Amount> amount = new ArrayList<>();
                amount.add(Amount.builder().name("total").
                        value(PaymentProcessingUtil.getAmount(paymentRequest.getAmount())).build()); // input Req amount
                amount.add(Amount.builder().name("authorizedAmount").
                        value(PaymentProcessingUtil.getAmount(routerGatewayResult.getAmount())).build()); // authorized amount
                resultList.add(Result.builder().gatewayResult(GatewayResult.builder()
                        .clientReferenceNumber(paymentRequest.getClientReferenceNumber())
                        .type(TransactionType.REFUND)
                        .groupId(payment.getGroupId())
                        .mgmId(routerGatewayResult.getMgmId()).amount(amount)
                        .card(Card.builder().maskedCardNumber(routerCardResponse.getTenderDisplay())
                                .mgmToken(paymentRequest.getPayment().getTenderDetails().getMgmToken())
                                .issuerType(routerCardResponse.getIssuerType())
                                .gatewayId(routerCardResponse.getGatewayId())
                                .securityCodeResult(routerCardResponse.getSecurityCodeResult())
                                .securityCodeValid(routerCardResponse.getSecurityCodeValid())
                                .build())
                        .dateTime(routerGatewayResult.getDateTime())
                        .transactionStatus(payment.getTransactionStatus().toString())
                        .transactionCode(payment.getTransactionStatus().getStatusCode())
                        .transaction(Transaction.builder()
                                .authorizationCode(payment.getPaymentAuthId())
                                .paymentId(payment.getPaymentId())
                                .avsResult(transactionResponse.getAvsResult())
                                .avsValid(transactionResponse.getAvsValid())
                                .gatewayResponse(transactionResponse.getGatewayResponse())
                                .responseCode(transactionResponse.getResponseCode()).build())
                        .build()).build());

            }

            paymentResponse.setStatusCode(SUCCESS_RESPONSE_STATUS_CODE.toString());
            paymentResponse.setStatusDesc(SUCCESS_RESPONSE_STATUS);
            paymentResponse.setResults(resultList);
        } catch (Exception e) {
            throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                    .dateTime(ZonedDateTime.now()).errorCode(PAYMENT_ROUTER_RESPONSE_PROCESSING_EXCEPTION.getCode()).errorMessage(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getDescription())
                    .developerMessage("Exception while updating PPS response with the response from Router").build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Update DB column transaction status to FAILURE, if exception occurs while router call
     *
     * @param payment - db record
     * @param e - exception
     */
    @Override
    public void updateDBOnFailure(PaymentEntity payment, HeadersDTO headersDTO, Exception e) {
        if (payment != null && (TransactionStatus.IN_PROCESS.equals(payment.getTransactionStatus()))) {
            refundRedisPaymentRepositoryWrapper.deleteById(payment.getClientReferenceNumber(), headersDTO);
            PaymentProcessingUtil.getFailureEntity(payment, e);
            repository.save(payment, headersDTO);
        }
    }

    private void publishAuditData(String[] errorEventData, PaymentRequest paymentRequest, Object requestPayload, Object responsePayload,
                                  PaymentEntity paymentEntity, LocalDateTime startTime, HeadersDTO headersDTO){
        String errorCode = errorEventData[0];
        String errorDescription = errorEventData[1];
        String eventName = errorEventData[2];
        String eventDescription = errorEventData[3];
        String result = errorEventData[4];
        String status = errorEventData[5];
        AuditData auditData = PaymentProcessingUtil.buildAuditData(eventName, eventDescription, errorCode, errorDescription,
                requestPayload, responsePayload, paymentEntity);
        auditData.setStartTimeTS(startTime);
        auditData.setResult(result);
        auditData.setStatus(status);
        String gatewayChainId = paymentEntity!=null ? paymentEntity.getGatewayChainId() : null;
        auditMapper.createAndPublishAuditTrailForRequestResponse(paymentRequest.getClientReferenceNumber(), gatewayChainId,
                paymentRequest.getSessionId(), paymentRequest.getMgmId(),"", headersDTO, auditData);
    }


}
