package com.mgm.payments.processing.service.service.processor;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.entity.redis.PaymentRedisEntity;
import com.mgm.payments.processing.service.entity.redis.VoidRedisEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.external.PaymentRouterServiceCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.pps.*;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.*;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.payment.RedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.voidcall.VoidRedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import com.mgm.payments.processing.service.util.SnowFlakeSequenceGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
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
import static com.mgm.payments.processing.service.enums.ApiErrorCode.PAYMENT_ROUTER_RESPONSE_PROCESSING_EXCEPTION;
import static com.mgm.payments.processing.service.enums.ApiErrorCode.PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION;


@Component
public class VoidPaymentProcessor implements PaymentProcessor {
    private final Logger logger = LoggerFactory.getLogger(VoidPaymentProcessor.class);

    PaymentProcessingRepositoryWrapper repository;
    private final PaymentRouterServiceCaller routerServiceCaller;
    private final AuditMapper auditMapper;
    private final VoidRedisPaymentRepositoryWrapper voidRedisPaymentRepositoryWrapper;
    private final RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper;
    Tracer tracer;
    private final SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;

    @Autowired
    public VoidPaymentProcessor(PaymentProcessingRepositoryWrapper repository, PaymentRouterServiceCaller routerServiceCaller,
                                AuditMapper auditMapper, VoidRedisPaymentRepositoryWrapper voidRedisPaymentRepositoryWrapper, RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper, Tracer tracer, SnowFlakeSequenceGenerator snowFlakeSequenceGenerator) {
        this.repository = repository;
        this.routerServiceCaller = routerServiceCaller;
        this.auditMapper = auditMapper;
        this.voidRedisPaymentRepositoryWrapper = voidRedisPaymentRepositoryWrapper;
        this.redisPaymentRepositoryWrapper = redisPaymentRepositoryWrapper;
        this.tracer = tracer;
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

    public Mono<PaymentResponse> process(PaymentRequest paymentRequest, User user, HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        final PaymentResponse paymentResponse = new PaymentResponse();
        final PaymentEntity paymentEntity = new PaymentEntity();
        List<PaymentEntity> paymentList;
        LocalDateTime startTime = LocalDateTime.now();
        String maskedRequest = LogMaskingConverter.mask(paymentRequest);
        String mgmId= paymentRequest.getMgmId() != null ? paymentRequest.getMgmId() : "";
        Throwable[] voidTransactionException = new Throwable[1];
        if (logger.isInfoEnabled()) {
            logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION, VOID_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), maskedRequest);
        }
        publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_VOID, "Void the given Amount", "", ""}, paymentRequest, maskedRequest,
                null, paymentEntity, startTime, headersDTO);

        try {
            Optional<PaymentRedisEntity> paymentRedisEntityOp = redisPaymentRepositoryWrapper.findById(paymentRequest.getPaymentId(), headersDTO);
            if(paymentRedisEntityOp.isPresent()){
                logger.info(PPS_REQUEST_INFO_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION , VOID_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), "Corresponding Auth Record Found in Payment Redis");
                PaymentRedisEntity paymentRedisEntity = paymentRedisEntityOp.get();
                validateRedisRecord(paymentRedisEntity, headersDTO);
                paymentList = PaymentProcessingUtil.mapToEntityList(paymentRedisEntity);
            }else {
                paymentList = repository.findByPaymentIdOrReferenceId(
                        paymentRequest.getPaymentId(), paymentRequest.getPaymentId(), headersDTO);
                validateInputRequest(paymentRequest, paymentList, headersDTO);
            }

            PaymentEntity paymentEntityObj = repository.findByPaymentId(paymentRequest.getPaymentId(),headersDTO);

            if(ObjectUtils.isEmpty(paymentEntityObj)){
                handleException(paymentRequest, headersDTO, new NullPointerException(PaymentProcessingConstants.PAYMENT_ID_NOT_FOUND), paymentResponse, paymentEntity, startTime);
            } else {
                headersDTO.setClientId(paymentEntityObj.getClientId());
            }

            populatePaymentEntity(headersDTO, paymentRequest, paymentList, paymentEntity, user);
            createRedisCacheEntry(paymentEntity, headersDTO);
            Mono<PaymentRouterResponse> prResponseMono = invokePaymentRouter(paymentEntity,
                    paymentRequest, headersDTO);
            return prResponseMono.map(prResponse ->
            {
                updateRouterResponseInTheDBRecord(paymentEntity, prResponse, user, headersDTO, paymentList);
                mapPaymentRouterResponseToPaymentResponse(paymentEntity, paymentRequest,
                        prResponse, paymentResponse, headersDTO);
                String status = StatusResult.F.name();
                String result = StatusResult.F.getResult();
                String responseCode = prResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
                if(responseCode.equals(RouterResponseCode.A.toString())){
                    status = StatusResult.S.name();
                    result = StatusResult.S.getResult();
                }
                String maskedResponse = LogMaskingConverter.mask(paymentResponse);
                publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_VOID, "Void the given Amount", result, status}, paymentRequest, maskedRequest,
                        maskedResponse, paymentEntity, startTime, headersDTO);
                stopWatch.stop();
                logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION, VOID_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), status, result, stopWatch.getTotalTimeMillis(),
                        maskedResponse);
                return paymentResponse;

            }).onErrorResume(throwable -> {
                voidTransactionException[0] = throwable;
                handleException(paymentRequest, headersDTO, throwable, paymentResponse, paymentEntity, startTime);
                return Mono.error(throwable);
            }).doFinally(signalType ->
            {
                updateDBOnFailure(paymentEntity, headersDTO, (Exception) voidTransactionException[0]);
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

    private void validateRedisRecord(PaymentRedisEntity paymentRedisEntity, HeadersDTO headersDTO) {
        if (voidRedisPaymentRepositoryWrapper.findById(paymentRedisEntity.getId(), headersDTO).isPresent()) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getCode(),
                    ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
        }
        if (paymentRedisEntity.getTransactionStatus() != TransactionStatus.SUCCESS) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getCode(),
                    ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }
        if(Boolean.TRUE.equals(paymentRedisEntity.getIsVoid())){
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_VOID_MESSAGE.getCode(),
                    ApiErrorCode.DUPLICATE_VOID_MESSAGE.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
        }
        if(Boolean.TRUE.equals(paymentRedisEntity.getIsCapture())){
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.VOID_ALREADY_CAPTURED.getCode(),
                    ApiErrorCode.VOID_ALREADY_CAPTURED.getDescription(), HttpStatus.PRECONDITION_FAILED);
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
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION, VOID_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", pe.getExceptionResponse());
            mgmErrorCode = pe.getExceptionResponse().getErrorCode();
            errorDescription = pe.getExceptionResponse().getErrorMessage();
        } else {
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION, VOID_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", e != null ? e.getMessage() : "Exception Occurred");
        }

        publishAuditData(new String[]{mgmErrorCode, errorDescription, AuditTrailConstants.PPS_VOID, "Exception during Void of the given Amount", StatusResult.E.getResult(), StatusResult.E.name()}, paymentRequest, maskedRequest,
                maskedResponse, paymentEntity, startTime, headersDTO);

    }


    /**
     * validate input request for Void call
     *
     * @param paymentRequest- input request
     * @param paymentList-    DB record
     */

    public void validateInputRequest(PaymentRequest paymentRequest, List<PaymentEntity> paymentList, HeadersDTO headersDTO) {

        if (voidRedisPaymentRepositoryWrapper.findById(paymentRequest.getPaymentId(), headersDTO).isPresent()) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_VOID_MESSAGE.getCode(),
                    ApiErrorCode.DUPLICATE_VOID_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }

        Optional<PaymentEntity> authorizeRecordOpt =
                paymentList.stream().filter(paymentEntity ->
                        paymentEntity.getPaymentId().equals(paymentRequest.getPaymentId())
                                && paymentEntity.getTransactionType().equals(TransactionType.AUTHORIZE)
                                && (paymentEntity.getTransactionStatus().equals(TransactionStatus.SUCCESS)
                                || paymentEntity.getTransactionStatus().equals(TransactionStatus.PARTIAL))).findFirst();

        if (authorizeRecordOpt.isPresent()) {
            Optional<PaymentEntity> captureOrVoidRecordOpt =
                    paymentList.stream().filter(paymentEntity -> authorizeRecordOpt.get().getPaymentId().
                            equals(paymentEntity.getReferenceId())
                            && (paymentEntity.getTransactionType().equals(TransactionType.CAPTURE)
                            || paymentEntity.getTransactionType().equals(TransactionType.VOID))
                            && !paymentEntity.getTransactionStatus().equals(TransactionStatus.FAILURE)).findFirst();
            if (captureOrVoidRecordOpt.isPresent()) {
                if (TransactionType.CAPTURE.equals(captureOrVoidRecordOpt.get().getTransactionType())) {
                    PaymentProcessingUtil.throwException(
                            ApiErrorCode.VOID_ALREADY_CAPTURED.getCode(),
                            ApiErrorCode.VOID_ALREADY_CAPTURED.getDescription(), HttpStatus.PRECONDITION_FAILED);
                } else {
                    PaymentProcessingUtil.throwException(
                            ApiErrorCode.DUPLICATE_VOID_MESSAGE.getCode(),
                            ApiErrorCode.DUPLICATE_VOID_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);
                }
            }
        } else {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.INVALID_VOID_MESSAGE.getCode(),
                    ApiErrorCode.INVALID_VOID_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }

    }

    /**
     * NOT REQUIRED FOR NOW
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
     * Insert the void transaction details to payment table with transaction_status as IN_PROCESS, before calling router-service
     *
     * @param headersDTO  -     headers params
     * @param request     - input request
     * @param paymentList -    DB records
     * @param user      -     user details
     *
     */

    public void populatePaymentEntity(HeadersDTO headersDTO, PaymentRequest request, List<PaymentEntity> paymentList,
                                      PaymentEntity paymentEntity, User user) {
        Optional<PaymentEntity> latestPaymentRecordOpt = paymentList.stream()
                .filter(entity -> TransactionType.AUTHORIZE.equals(entity.getTransactionType())).findFirst();
        PaymentEntity authPayment = latestPaymentRecordOpt.orElse(null);
        if (authPayment == null) {
            PaymentProcessingUtil.throwException(ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getCode(),
                    ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
        }
        assert authPayment != null;
        String gatewayRRN = authPayment.getGatewayChainId();
        paymentEntity.setPaymentId(PaymentProcessingUtil.generateUniqueId(snowFlakeSequenceGenerator.nextId()));
        paymentEntity.setClientReferenceNumber(request.getClientReferenceNumber());
        paymentEntity.setReferenceId(authPayment.getPaymentId());
        paymentEntity.setGatewayChainId(gatewayRRN);
        paymentEntity.setGroupId(authPayment.getGroupId());
        paymentEntity.setGatewayId(authPayment.getGatewayId());
        paymentEntity.setAmount(authPayment.getAmount());
        paymentEntity.setAuthChainId(0);
        paymentEntity.setClerkId("1");
        paymentEntity.setClientId(headersDTO.getClientId()); // need to confirm
        paymentEntity.setOrderType(authPayment.getOrderType());
        paymentEntity.setMgmToken(authPayment.getMgmToken());
        paymentEntity.setMgmId(authPayment.getMgmId());
        paymentEntity.setCardHolderName(authPayment.getCardHolderName());
        paymentEntity.setTenderType(authPayment.getTenderType());
        paymentEntity.setCardEntryMode(authPayment.getCardEntryMode());
        paymentEntity.setLast4DigitsOfTheCard(authPayment.getLast4DigitsOfTheCard());
        paymentEntity.setIssuerType(authPayment.getIssuerType());
        paymentEntity.setCurrencyCode(authPayment.getCurrencyCode());
        paymentEntity.setBillingAddress1(authPayment.getBillingAddress1());
        paymentEntity.setBillingAddress2(authPayment.getBillingAddress2());
        paymentEntity.setBillingCity(authPayment.getBillingCity());
        paymentEntity.setBillingCountry(authPayment.getBillingCountry());
        paymentEntity.setBillingState(authPayment.getBillingState());
        paymentEntity.setBillingZipcode(authPayment.getBillingZipcode());
        paymentEntity.setTransactionType(TransactionType.VOID);
        paymentEntity.setTransactionStatus(TransactionStatus.IN_PROCESS);
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
        voidRedisPaymentRepositoryWrapper.save(VoidRedisEntity.builder().id(paymentEntity.getReferenceId())
                .transactionStatus(TransactionStatus.IN_PROCESS)
                .build(), headersDTO);
    }

    /**
     * invokes external router service call for void
     *
     * @param paymentEntity-  DB record
     * @param paymentRequest- input request
     * @param headersDTO-     headers params
     * @return PaymentRouterResponse response sent from router service call
     */

    public Mono<PaymentRouterResponse> invokePaymentRouter(PaymentEntity paymentEntity, PaymentRequest paymentRequest, HeadersDTO headersDTO) {
        LocalDateTime startTime = LocalDateTime.now();
        Payment payment = paymentRequest.getPayment();
        if (payment == null) {
            payment = Payment.builder().tenderDetails(TenderDetails.builder().mgmToken(paymentEntity.getMgmToken()).build()).build();
        }
        PaymentRouterRequest paymentRouterRequest = PaymentRouterRequest.builder()
                .routerFunction(RouterFunction.VOID)
                .mgmId(paymentEntity.getMgmId())
                .sessionId(paymentRequest.getSessionId())
                .gatewayChainId(paymentEntity.getGatewayChainId())
                .gatewayId(paymentEntity.getGatewayId())
                .amount(List.of(Amount.builder().name(TOTAL_AMOUNT).value(paymentEntity.getAmount()).build()))
                .payment(payment)
                .hotelData(paymentRequest.getHotelData())
                .merchantReferenceCode(paymentRequest.getPaymentId())
                .build();
        String maskedRequest = LogMaskingConverter.mask(paymentRouterRequest);
        publishAuditData(new String[]{"", "", "PPS_TO_PR_VOID", "PPS to Payment Router - Void the given Amount", "", ""}, paymentRequest, maskedRequest,
                null, paymentEntity, startTime, headersDTO);

        Mono<ResponseEntity<PaymentRouterResponse>> paymentRouterResponseMono = routerServiceCaller.invokeRouter(paymentRouterRequest, headersDTO, paymentEntity.getPaymentId());
        return paymentRouterResponseMono.map(paymentRouterResponseResponseEntity -> {
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
            publishAuditData(new String[]{"", "", "PPS_TO_PR_VOID", "PPS to Payment Router - Void the given Amount", result, status}, paymentRequest, maskedRequest,
                    maskedResponse, paymentEntity, startTime, headersDTO);
            return paymentRouterResponse;
        });
    }

    /**
     * Updates the void record in database with router response
     *
     * @param payment    -    database record
     * @param prResponse - router service response
     * @param user      - user details
     */

    public void updateRouterResponseInTheDBRecord(PaymentEntity payment, PaymentRouterResponse prResponse, User user, HeadersDTO headersDTO, List<PaymentEntity> paymentList) {
        try {
            Optional<RouterResult> optionalRouterResult = prResponse.getResults().stream().findFirst();
            RouterGatewayResult gatewayResult = optionalRouterResult.map(RouterResult::getGatewayResult).orElse(null);
            assert gatewayResult != null;
            payment.setGatewayId(gatewayResult.getCard().getGatewayId());
            payment.setCvvResponseCode(gatewayResult.getCard().getSecurityCodeResult());
            payment.setGatewayChainId(gatewayResult.getTransaction().getGatewayChainId());
            payment.setPaymentAuthId(gatewayResult.getTransaction().getAuthorizationCode());
            TransactionStatus transactionStatus = TransactionStatus.valueOf(RouterResponseCode.valueOf(gatewayResult.getTransaction().getResponseCode()).getStatus());
            payment.setTransactionStatus(transactionStatus);
            BigDecimal amount = PaymentProcessingUtil.getAmount(gatewayResult.getAmount());
            payment.setAuthorizedAmount(amount != null && !TransactionStatus.FAILURE.equals(transactionStatus) ? amount : new BigDecimal(0));
            RouterTransactionResponse transactionResponse = gatewayResult.getTransaction();
            payment.setGatewayTransactionStatusCode(transactionResponse.getGatewayResponse().getReasonCode());// 200 for success response
            payment.setGatewayTransactionStatusReason(transactionResponse.getGatewayResponse().getReasonDescription());
            payment.setGatewayResponseCode(transactionResponse.getResponseCode());
            payment.setGatewayRrn(transactionResponse.getRetrievalReference());
            payment.setDeferredAuth(transactionResponse.getDeferredAuth());
            payment.setPaymentAuthSource(transactionResponse.getAuthSource());
            payment.setAvsResponseCode(transactionResponse.getAvsResult());
            payment.setUpdatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
            payment.setUpdatedBy(user.getServiceId());
            saveResponseToDB(payment, prResponse, transactionStatus, headersDTO, paymentList);
            voidRedisPaymentRepositoryWrapper.deleteById(payment.getReferenceId(), headersDTO);
        } catch (Exception e) {
            throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                    .dateTime(ZonedDateTime.now()).errorCode(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getCode()).errorMessage(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getDescription())
                    .developerMessage("Exception while updating DB with the response from Router").build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void saveResponseToDB(PaymentEntity payment, PaymentRouterResponse prResponse, TransactionStatus transactionStatus, HeadersDTO headersDTO, List<PaymentEntity> paymentList) {
        try {
            if(transactionStatus.equals(TransactionStatus.SUCCESS)) {
                PaymentEntity authEntity = PaymentProcessingUtil.getAuthEntity(paymentList);
                if (authEntity != null) {
                    PaymentRedisEntity paymentRedisEntity = PaymentProcessingUtil.mapToRedisEntity(authEntity);
                    paymentRedisEntity.setIsVoid(Boolean.TRUE);
                    redisPaymentRepositoryWrapper.save(paymentRedisEntity, headersDTO);
                }
            }
            repository.save(payment, headersDTO);
        }catch(Exception e){
            String maskedResponse = LogMaskingConverter.mask(prResponse);
            if(transactionStatus.equals(TransactionStatus.SUCCESS)){
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION, VOID_CLASS_NAME, "SUCCESS_VOID_DB_EXCEPTION",
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.S.name(), StatusResult.S.getResult(), "",
                        e.getMessage());
                publishAuditData(new String[]{"", "", "SUCCESS_VOID_DB_EXCEPTION", "Exception while updating the DB with the " +
                        "successful void response", StatusResult.S.getResult(), StatusResult.S.name()}, PaymentRequest.builder().clientReferenceNumber(payment
                                .getClientReferenceNumber()).sessionId(payment.getSessionId()).mgmId(payment.getMgmId())
                        .build(), null, maskedResponse, payment, LocalDateTime.now(), new HeadersDTO(
                        "PPS_VOID_DB_ENTRY_EXCEPTION", payment.getMgmJourneyId(),
                        payment.getMgmCorrelationId(), payment.getMgmTransactionId(), payment.getRequestChannel(),
                        null, payment.getClientId(), "user-agent")
                );
            } else {
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, VOID_OPERATION, VOID_CLASS_NAME, "FAILED_VOID_DB_EXCEPTION",
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.F.name(), StatusResult.F.getResult(), "",
                        e.getMessage());
                publishAuditData(new String[]{"", "", "FAILED_VOID_DB_EXCEPTION", "Exception while updating the DB with the " +
                        "failed void response", StatusResult.F.getResult(), StatusResult.F.name()}, PaymentRequest.builder().clientReferenceNumber(payment
                                .getClientReferenceNumber()).sessionId(payment.getSessionId()).mgmId(payment.getMgmId())
                        .build(), null, maskedResponse, payment, LocalDateTime.now(), new HeadersDTO(
                        "PPS_VOID_DB_ENTRY_EXCEPTION", payment.getMgmJourneyId(),
                        payment.getMgmCorrelationId(), payment.getMgmTransactionId(), payment.getRequestChannel(),
                        null, payment.getClientId(), "user-agent")
                );
            }
        }
    }

    /**
     * Maps Router Response to PaymentResponse and returns
     *
     * @param payment-        DB record
     * @param paymentRequest- input request
     * @param prResponse-     routerResponse
     *
     */
    @Override
    public void mapPaymentRouterResponseToPaymentResponse(PaymentEntity payment, PaymentRequest paymentRequest,
                                                          PaymentRouterResponse prResponse, PaymentResponse paymentResponse, HeadersDTO headersDTO) {

        try {
            List<RouterResult> routerResultList = prResponse.getResults();
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
                        .clientReferenceNumber(paymentRequest.getClientReferenceNumber())
                        .type(TransactionType.VOID)
                        .mgmId(routerGatewayResult.getMgmId()).amount(amount)
                        .card(Card.builder()
                                .maskedCardNumber(routerCardResponse.getTenderDisplay())
                                .gatewayId(routerCardResponse.getGatewayId())
                                .issuerType(routerCardResponse.getIssuerType())
                                .securityCodeResult(routerCardResponse.getSecurityCodeResult())
                                .securityCodeValid(routerCardResponse.getSecurityCodeValid())
                                .build())
                        .dateTime(routerGatewayResult.getDateTime())
                        .transactionStatus(payment.getTransactionStatus().toString())
                        .transactionCode(payment.getTransactionStatus().getStatusCode())
                        .transaction(Transaction.builder()
                                .authorizationCode(payment.getPaymentAuthId())
                                .paymentId(paymentRequest.getPaymentId())
                                .avsResult(transactionResponse.getAvsResult())
                                .avsValid(transactionResponse.getAvsValid())
                                .gatewayResponse(transactionResponse.getGatewayResponse())
                                .responseCode(transactionResponse.getResponseCode()).build())
                        .groupId(payment.getGroupId()).build()).build());

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
     * Update db record with transaction_status as FAILURE if exception occurs while router-service call
     *
     * @param payment - db record
     * @param e - exception
     */
    public void updateDBOnFailure(PaymentEntity payment, HeadersDTO headersDTO, Exception e) {
        if (payment != null && (TransactionStatus.IN_PROCESS.equals(payment.getTransactionStatus()))) {
            voidRedisPaymentRepositoryWrapper.deleteById(payment.getReferenceId(), headersDTO);
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
        AuditData auditData = PaymentProcessingUtil.buildAuditData(eventName, eventDescription, errorCode, errorDescription,
                requestPayload, responsePayload, paymentEntity);
        auditData.setStartTimeTS(startTime);
        auditData.setResult(result);
        auditData.setStatus(status);
        String gatewayChainId = paymentEntity != null ? paymentEntity.getGatewayChainId() : null;
        auditMapper.createAndPublishAuditTrailForRequestResponse(paymentRequest.getClientReferenceNumber(), gatewayChainId,
                paymentRequest.getSessionId(), paymentRequest.getMgmId(), "", headersDTO, auditData);
    }

}
