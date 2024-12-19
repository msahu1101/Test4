package com.mgm.payments.processing.service.service.processor;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.entity.redis.CaptureRedisEntity;
import com.mgm.payments.processing.service.entity.redis.PaymentRedisEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.events.ConfirmEvent;
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
import com.mgm.payments.processing.service.repository.redis.capture.CaptureRedisPaymentRepositoryWrapper;
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

@Component
public class CapturePaymentProcessor implements PaymentProcessor {

    private final Logger logger = LoggerFactory.getLogger(CapturePaymentProcessor.class);
    PaymentProcessingRepositoryWrapper repository;
    private final PaymentRouterServiceCaller routerServiceCaller;
    private final CaptureRedisPaymentRepositoryWrapper capturePaymentRepositoryWrapper;
    private final AuditMapper auditMapper;
    private final CaptureConfirmEventListener captureConfirmEventService;
    private final RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper;
    Tracer tracer;
    private final ClientConfigurationServiceCaller clientConfigurationServiceCaller;
    private final SessionServiceCaller sessionServiceCaller;
    private final SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;

    @Autowired
    public CapturePaymentProcessor(PaymentProcessingRepositoryWrapper repository, PaymentRouterServiceCaller routerServiceCaller,
                                   CaptureRedisPaymentRepositoryWrapper capturePaymentRepositoryWrapper, AuditMapper auditMapper,
                                   CaptureConfirmEventListener captureConfirmEventService, RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper, Tracer tracer, ClientConfigurationServiceCaller clientConfigurationServiceCaller, SessionServiceCaller sessionServiceCaller, SnowFlakeSequenceGenerator snowFlakeSequenceGenerator) {
        this.repository = repository;
        this.routerServiceCaller = routerServiceCaller;
        this.capturePaymentRepositoryWrapper = capturePaymentRepositoryWrapper;
        this.auditMapper = auditMapper;
        this.captureConfirmEventService = captureConfirmEventService;
        this.redisPaymentRepositoryWrapper = redisPaymentRepositoryWrapper;
        this.tracer = tracer;
        this.clientConfigurationServiceCaller = clientConfigurationServiceCaller;
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
            PaymentResponse paymentResponse = new PaymentResponse();
            final PaymentEntity paymentEntity = new PaymentEntity();
            PaymentEntity authRecord;
            LocalDateTime startTime = LocalDateTime.now();
            String maskedRequest = LogMaskingConverter.mask(paymentRequest);
            String mgmId = paymentRequest.getMgmId() != null ? paymentRequest.getMgmId() : "";
            Throwable[] captureTransactionException = new Throwable[1];
            List<PaymentEntity> paymentList;
            if (logger.isInfoEnabled()) {
                logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION, CAPTURE_CLASS_NAME, headersDTO.getMgmSource(),
                        headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                        headersDTO.getMgmTransactionId(),headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), maskedRequest);
            }
            publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_CAPTURE, "Capture the given Amount", "", ""}, paymentRequest, maskedRequest,
                    null, paymentEntity, startTime, headersDTO);
            try {
                Optional<PaymentRedisEntity> paymentRedisEntityOp = redisPaymentRepositoryWrapper.findById(paymentRequest.getPaymentId(), headersDTO);
                if(paymentRedisEntityOp.isPresent()){
                    logger.info(PPS_REQUEST_INFO_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION , CAPTURE_CLASS_NAME, headersDTO.getMgmSource(),
                            headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                            headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), "Corresponding Auth Record Found in Payment Redis");
                    PaymentRedisEntity paymentRedisEntity = paymentRedisEntityOp.get();
                    validateRedisRecord(paymentRedisEntity, paymentRequest, headersDTO);
                    paymentList = PaymentProcessingUtil.mapToEntityList(paymentRedisEntity);
                }else {
                    paymentList = repository.findByPaymentIdOrReferenceId(
                            paymentRequest.getPaymentId(), paymentRequest.getPaymentId(), headersDTO);
                    validateInputRequest(paymentRequest, paymentList, headersDTO);
                }
                if(!PAM_SERVICE.equals(headersDTO.getMgmSource())) {
                    PaymentSession paymentSession = retrieveClientDetailsFromSession(paymentRequest, headersDTO);
                    PaymentProcessingUtil.mapDerivedClientId(paymentSession, headersDTO, paymentRequest);
                    ClientConfigPayload clientConfig = clientConfigurationServiceCaller.getClientConfig(headersDTO);
                    headersDTO.setClientId(clientConfig.getClientId());
                }
                Optional<PaymentEntity> authRecordOpt = paymentList.stream()
                        .filter(entity -> entity.getTransactionType().equals(TransactionType.AUTHORIZE)).findFirst();
                authRecord = authRecordOpt.orElse(null);
                populatePaymentEntity(headersDTO, paymentRequest, paymentList, paymentEntity, user);
                createRedisCacheEntry(paymentEntity, headersDTO);
                Mono<PaymentRouterResponse> prResponseMono = invokePaymentRouter(paymentEntity,
                        paymentRequest, headersDTO);
                return prResponseMono.map(paymentRouterResponse ->
                {
                    updateRouterResponseInTheDBRecord(paymentEntity, paymentRouterResponse, user, headersDTO, paymentList);
                    mapPaymentRouterResponseToPaymentResponse(paymentEntity, paymentRequest,
                            paymentRouterResponse, paymentResponse, headersDTO);
                    if (!headersDTO.getMgmSource().equals(PaymentProcessingConstants.PAM_SERVICE)) {
                        paymentRequest.setClientReferenceNumber(paymentEntity.getClientReferenceNumber());
                        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
                        assert authRecord != null;
                        captureConfirmService(paymentRequest, authRecord.getSessionId(), headersDTO, gatewayResult.getTransactionCode(),
                                gatewayResult.getTransaction());
                    }
                    String maskedResponse = LogMaskingConverter.mask(paymentResponse);
                    String status = StatusResult.F.name();
                    String result = StatusResult.F.getResult();
                    String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
                    if(responseCode.equals(RouterResponseCode.A.toString())){
                        status = StatusResult.S.name();
                        result = StatusResult.S.getResult();
                    }
                    publishAuditData(new String[]{"", "", AuditTrailConstants.PPS_CAPTURE, "Capture the given Amount", result, status}, paymentRequest, maskedRequest,
                            maskedResponse, paymentEntity, startTime, headersDTO);
                    stopWatch.stop();
                    logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION, CAPTURE_CLASS_NAME, headersDTO.getMgmSource(),
                            headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                            headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), status, result, stopWatch.getTotalTimeMillis(),
                            maskedResponse);
                    return paymentResponse;
                }).onErrorResume(throwable -> {
                    captureTransactionException[0] = throwable;
                    handleException(paymentRequest, headersDTO, throwable, paymentResponse, paymentEntity, startTime);
                    return Mono.error(throwable);
                }).doFinally(signalType ->
                {
                    updateDBOnFailure(paymentEntity, headersDTO, (Exception) captureTransactionException[0]);
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

    private void validateRedisRecord(PaymentRedisEntity paymentRedisEntity, PaymentRequest paymentRequest, HeadersDTO headersDTO) {
        if (capturePaymentRepositoryWrapper.findById(paymentRedisEntity.getId(), headersDTO).isPresent()) {
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
        if(Boolean.TRUE.equals(paymentRedisEntity.getIsCapture())){
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getCode(),
                    ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
        }
        if(Boolean.TRUE.equals(paymentRedisEntity.getIsVoid())){
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.CAPTURE_ALREADY_VOID.getCode(),
                    ApiErrorCode.CAPTURE_ALREADY_VOID.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }
        if(!paymentRedisEntity.getAuthorizedAmount().equals(PaymentProcessingUtil.getAmount(paymentRequest.getAmount()))){
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.CAPTURE_AUTH_AMOUNT_MISMATCH.getCode(),
                    ApiErrorCode.CAPTURE_AUTH_AMOUNT_MISMATCH.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
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
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION, CAPTURE_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", pe.getExceptionResponse());
            mgmErrorCode = pe.getExceptionResponse().getErrorCode();
            errorDescription = pe.getExceptionResponse().getErrorMessage();
        }else {
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION, CAPTURE_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(),
                    "", e != null ? e.getMessage() : "Exception Occurred");
        }
        publishAuditData(new String[]{mgmErrorCode, errorDescription, AuditTrailConstants.PPS_CAPTURE, "Exception while Capturing the given Amount", StatusResult.E.getResult(), StatusResult.E.name()}, paymentRequest, maskedRequest,
                maskedResponse, paymentEntity, startTime, headersDTO);

        if (paymentEntity != null && !headersDTO.getMgmSource().equals(PaymentProcessingConstants.PAM_SERVICE)) {
                paymentRequest.setClientReferenceNumber(paymentRequest.getClientReferenceNumber());
                captureConfirmService(paymentRequest, paymentEntity.getSessionId(), headersDTO, TransactionStatus.FAILURE.toString(),
                        Transaction.builder().responseCode(null).avsResult(null).build());
        }
    }

    /**
     * Validates the input request for capture call
     *
     * @param paymentRequest- input request
     * @param paymentList-    DB table record
     */
    public void validateInputRequest(PaymentRequest paymentRequest, List<PaymentEntity> paymentList, HeadersDTO headersDTO) {

        if (capturePaymentRepositoryWrapper.findById(paymentRequest.getPaymentId(), headersDTO).isPresent()) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getCode(),
                    ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
        }
        Optional<PaymentEntity> authorizeRecordOpt =
                paymentList.stream().filter(paymentEntity -> paymentEntity.getPaymentId().equals(paymentRequest.getPaymentId())
                        && paymentEntity.getTransactionType().equals(TransactionType.AUTHORIZE)
                        && (paymentEntity.getTransactionStatus().equals(TransactionStatus.SUCCESS)
                        || paymentEntity.getTransactionStatus().equals(TransactionStatus.PARTIAL))).findFirst();

        if (authorizeRecordOpt.isEmpty()) {
            PaymentProcessingUtil.throwException(
                    ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getCode(),
                    ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getDescription(), HttpStatus.PRECONDITION_FAILED);

        } else {

            if (!PaymentProcessingUtil.getAmount(paymentRequest.getAmount())
                    .equals(authorizeRecordOpt.get().getAuthorizedAmount())) {
                PaymentProcessingUtil.throwException(
                        ApiErrorCode.CAPTURE_AUTH_AMOUNT_MISMATCH.getCode(),
                        ApiErrorCode.CAPTURE_AUTH_AMOUNT_MISMATCH.getDescription(),
                        HttpStatus.PRECONDITION_FAILED);
            }
            Optional<PaymentEntity> captureOrVoidRecordOpt =
                    paymentList.stream().filter(paymentEntity -> authorizeRecordOpt.get().getPaymentId().
                            equals(paymentEntity.getReferenceId())
                            && (paymentEntity.getTransactionType().equals(TransactionType.CAPTURE)
                            || paymentEntity.getTransactionType().equals(TransactionType.VOID))
                            && !paymentEntity.getTransactionStatus().equals(TransactionStatus.FAILURE)).findFirst();
            if (captureOrVoidRecordOpt.isPresent() && TransactionType.CAPTURE.equals(captureOrVoidRecordOpt.get().getTransactionType())) {
                    PaymentProcessingUtil.throwException(
                            ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getCode(),
                            ApiErrorCode.DUPLICATE_CAPTURE_MESSAGE.getDescription(),
                            HttpStatus.PRECONDITION_FAILED);
            } else if (captureOrVoidRecordOpt.isPresent() && TransactionType.VOID.equals(captureOrVoidRecordOpt.get().getTransactionType())){
                    PaymentProcessingUtil.throwException(
                            ApiErrorCode.CAPTURE_ALREADY_VOID.getCode(),
                            ApiErrorCode.CAPTURE_ALREADY_VOID.getDescription(), HttpStatus.PRECONDITION_FAILED);
            }
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
        PaymentSession paymentSession = sessionServiceCaller.retrieveSession(headersDTO, paymentRequest.getSessionId());
        publishAuditData(new String[]{"", "", "PPS_TO_SESSION", "PPS to Payment Session - Retrieve Session", "", ""}, paymentRequest, paymentRequest.getSessionId(),
                null, null, LocalDateTime.now(), headersDTO);
        String maskedResponse = LogMaskingConverter.mask(paymentSession);
        publishAuditData(new String[]{"", "", "PPS_TO_SESSION", "PPS to Payment Session - Retrieve Session", StatusResult.S.getResult(), StatusResult.S.name()}, paymentRequest, paymentRequest.getSessionId(),
                maskedResponse, null, LocalDateTime.now(), headersDTO);
        return paymentSession;
    }

    /**
     * Saves the capture record to database with transaction status as IN PROCESS, before calling router-service
     *
     * @param headersDTO  -     headers params
     * @param request     - input request
     * @param paymentList -    database record
     * @param user       -     user details
     */

    public void populatePaymentEntity(HeadersDTO headersDTO, PaymentRequest request, List<PaymentEntity> paymentList,
                                      PaymentEntity captureEntity, User user) {
        Optional<PaymentEntity> authorizeRecordOpt =
                paymentList.stream().filter(paymentEntity -> paymentEntity.getTransactionType() == TransactionType.AUTHORIZE).findFirst();

        PaymentEntity authEntity = authorizeRecordOpt.orElse(null);
        if(authEntity == null){
            PaymentProcessingUtil.throwException(ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getCode(),
                    ApiErrorCode.AUTHORIZE_NOT_EXIST_MESSAGE.getDescription(),
                    HttpStatus.PRECONDITION_FAILED);
        }
        assert authEntity != null;
        // create a capture record copy all the required details from authorize record
        captureEntity.setPaymentId(PaymentProcessingUtil.generateUniqueId(snowFlakeSequenceGenerator.nextId()));
        request.setMgmId(request.getMgmId() == null ? authEntity.getMgmId() : request.getMgmId());
        captureEntity.setClientReferenceNumber(request.getClientReferenceNumber());
        captureEntity.setReferenceId(authEntity.getPaymentId());
        captureEntity.setGatewayChainId(authEntity.getGatewayChainId());
        captureEntity.setGroupId(authEntity.getGroupId());
        captureEntity.setGatewayId(authEntity.getGatewayId());
        captureEntity.setAmount(PaymentProcessingUtil.getAmount(request.getAmount()));
        captureEntity.setAuthChainId(0);
        captureEntity.setClerkId("1");
        captureEntity.setClientId(headersDTO.getClientId());
        captureEntity.setOrderType(authEntity.getOrderType());
        captureEntity.setMgmId(authEntity.getMgmId());
        captureEntity.setMgmToken(authEntity.getMgmToken());
        captureEntity.setCardHolderName(authEntity.getCardHolderName());
        captureEntity.setTenderType(authEntity.getTenderType());
        captureEntity.setCardEntryMode(authEntity.getCardEntryMode());
        captureEntity.setLast4DigitsOfTheCard(authEntity.getLast4DigitsOfTheCard());
        captureEntity.setIssuerType(authEntity.getIssuerType());
        captureEntity.setCurrencyCode(authEntity.getCurrencyCode());
        captureEntity.setBillingAddress1(authEntity.getBillingAddress1());
        captureEntity.setBillingAddress2(authEntity.getBillingAddress2());
        captureEntity.setBillingCity(authEntity.getBillingCity());
        captureEntity.setBillingCountry(authEntity.getBillingCountry());
        captureEntity.setBillingState(authEntity.getBillingState());
        captureEntity.setBillingZipcode(authEntity.getBillingZipcode());
        captureEntity.setTransactionType(TransactionType.CAPTURE);
        captureEntity.setTransactionStatus(TransactionStatus.IN_PROCESS);
        captureEntity.setCreatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
        captureEntity.setUpdatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
        captureEntity.setCreatedBy(user.getServiceId());
        captureEntity.setUpdatedBy(user.getServiceId());
        captureEntity.setMgmCorrelationId(headersDTO.getMgmCorrelationId());
        captureEntity.setMgmJourneyId(headersDTO.getMgmJourneyId());
        captureEntity.setMgmTransactionId(headersDTO.getMgmTransactionId());
        captureEntity.setRequestChannel(headersDTO.getMgmChannel());
        captureEntity.setSessionId(request.getSessionId());
        captureEntity.setAuthSubtype("Normal");

    }


    public void createRedisCacheEntry(PaymentEntity paymentEntity, HeadersDTO headersDTO) {
        capturePaymentRepositoryWrapper.save(CaptureRedisEntity.builder().id(paymentEntity.getReferenceId())
                .transactionStatus(TransactionStatus.IN_PROCESS)
                .amount(paymentEntity.getAmount()).build(), headersDTO);
    }

    /**
     * Invokes External router-service calls for capture
     *
     * @param paymentEntity-  database record of capture call
     * @param paymentRequest- input request
     * @param headersDTO-     headers params
     * @return PaymentRouterResponse- Response from router-service
     */

    @Override
    public Mono<PaymentRouterResponse> invokePaymentRouter(PaymentEntity paymentEntity, PaymentRequest paymentRequest, HeadersDTO headersDTO) {
        LocalDateTime startTime = LocalDateTime.now();
        Payment payment = paymentRequest.getPayment();
        if (payment == null) {
            payment = Payment.builder().tenderDetails(TenderDetails.builder().mgmToken(paymentEntity.getMgmToken()).build()).build();
        }
        HotelData hotelData = paymentRequest.getHotelData();
        if(hotelData != null){
            hotelData.setFolioNumber(paymentRequest.getClientReferenceNumber());
        }
        PaymentRouterRequest paymentRouterRequest = PaymentRouterRequest.builder()
                .routerFunction(RouterFunction.CAPTURE)
                .amount(paymentRequest.getAmount())
                .clientReferenceNumber(paymentRequest.getClientReferenceNumber())
                .sessionId(paymentRequest.getSessionId())
                .mgmId(paymentRequest.getMgmId())
                .gatewayChainId(paymentEntity.getGatewayChainId())
                .gatewayId(paymentEntity.getGatewayId())
                .payment(payment)
                .hotelData(hotelData)
                .merchantReferenceCode(paymentRequest.getPaymentId())
                .additionalAttributes(paymentRequest.getAdditionalAttributes())
                .build();
        String maskedPrRequest = LogMaskingConverter.mask(paymentRouterRequest);
        publishAuditData(new String[]{"", "", "PPS_TO_PR_CAPTURE", "PPS to Payment Router - Capture the given Amount", "", ""}, paymentRequest, maskedPrRequest,
                null, paymentEntity, startTime, headersDTO);
        Mono<ResponseEntity<PaymentRouterResponse>> paymentRouterResponseMono = routerServiceCaller.invokeRouter(paymentRouterRequest, headersDTO, paymentEntity.getPaymentId());
        return paymentRouterResponseMono.map(paymentRouterResponseResponseEntity ->
        {
            PaymentRouterResponse paymentRouterResponse = paymentRouterResponseResponseEntity.getBody();
            String maskedPrResponse = LogMaskingConverter.mask(paymentRouterResponse);
            String result = StatusResult.F.getResult();
            String status = StatusResult.F.name();
            assert paymentRouterResponse != null;
            String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                result = StatusResult.S.getResult();
                status = StatusResult.S.name();
            }
            publishAuditData(new String[]{"", "", "PPS_TO_PR_CAPTURE", "PPS to Payment Router - Capture the given Amount", result, status}, paymentRequest, maskedPrRequest,
                    maskedPrResponse, paymentEntity, startTime, headersDTO);
            return paymentRouterResponse;
        });


    }

    /**
     * Update the database with router service response
     *
     * @param payment    -    database record of capture call
     * @param prResponse - payment router response
     * @param user      -    user details
     */
    public void updateRouterResponseInTheDBRecord(PaymentEntity payment, PaymentRouterResponse prResponse, User user, HeadersDTO headersDTO, List<PaymentEntity> paymentList) {
        Optional<RouterResult> optionalRouterResult = prResponse.getResults().stream().findFirst();
        try {
            RouterGatewayResult gatewayResult = optionalRouterResult.map(RouterResult::getGatewayResult).orElse(null);
            assert gatewayResult != null;
            RouterTransactionResponse transactionResponse = gatewayResult.getTransaction();
            TransactionStatus transactionStatus = TransactionStatus.valueOf(RouterResponseCode.valueOf(transactionResponse.getResponseCode()).getStatus());
            payment.setTransactionStatus(transactionStatus);
            payment.setCvvResponseCode(gatewayResult.getCard().getSecurityCodeResult());
            payment.setPaymentAuthId(transactionResponse.getAuthorizationCode());
            payment.setGatewayTransactionStatusCode(transactionResponse.getGatewayResponse().getReasonCode());
            payment.setGatewayTransactionStatusReason(transactionResponse.getGatewayResponse().getReasonDescription());
            payment.setGatewayResponseCode(transactionResponse.getResponseCode());
            payment.setGatewayRrn(transactionResponse.getRetrievalReference());
            payment.setDeferredAuth(transactionResponse.getDeferredAuth());
            payment.setPaymentAuthSource(transactionResponse.getAuthSource());
            payment.setPaymentAuthId(transactionResponse.getAuthorizationCode());
            payment.setAvsResponseCode(transactionResponse.getAvsResult());
            BigDecimal amount = PaymentProcessingUtil.getAmount(prResponse.getResults().get(0).getGatewayResult().getAmount());
            payment.setAuthorizedAmount(amount != null && !TransactionStatus.FAILURE.equals(transactionStatus) ? amount : new BigDecimal(0));
            payment.setUpdatedTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString());
            payment.setUpdatedBy(user.getServiceId());
            saveResponseInDB(payment, prResponse, transactionStatus, headersDTO, paymentList);
            capturePaymentRepositoryWrapper.deleteById(payment.getReferenceId(), headersDTO);
        } catch (Exception e) {
            throw new PaymentProcessingException(PaymentExceptionResponse.builder().paymentId(payment.getPaymentId())
                    .dateTime(ZonedDateTime.now()).errorCode(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getCode()).errorMessage(PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION.getDescription())
                    .developerMessage("Exception while updating DB with the response from Router").build(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    private void saveResponseInDB(PaymentEntity payment, PaymentRouterResponse prResponse, TransactionStatus transactionStatus, HeadersDTO headersDTO, List<PaymentEntity> paymentList) {
        try {
            if(transactionStatus.equals(TransactionStatus.SUCCESS)) {
                PaymentEntity authEntity = PaymentProcessingUtil.getAuthEntity(paymentList);
                if (authEntity != null) {
                    PaymentRedisEntity paymentRedisEntity = PaymentProcessingUtil.mapToRedisEntity(authEntity);
                    paymentRedisEntity.setIsCapture(Boolean.TRUE);
                    redisPaymentRepositoryWrapper.save(paymentRedisEntity, headersDTO);
                }
            }
            repository.save(payment, headersDTO);
        } catch (Exception e){
            String maskedResponse = LogMaskingConverter.mask(prResponse);
            if(transactionStatus.equals(TransactionStatus.SUCCESS)){
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION, CAPTURE_CLASS_NAME, PPS_CAPTURE_DB_ENTRY_EXCEPTION,
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.S.name(), StatusResult.S.getResult(), "",
                        e.getMessage());
               publishAuditData(new String[]{"", "", "SUCCESS_CAPTURE_DB_EXCEPTION", "Exception while updating the DB with the " +
                        "successful capture response", StatusResult.S.getResult(), StatusResult.S.name()}, PaymentRequest.builder().clientReferenceNumber(payment
                                .getClientReferenceNumber()).sessionId(payment.getSessionId()).mgmId(payment.getMgmId())

                        .build(), null, maskedResponse, payment, LocalDateTime.now(), new HeadersDTO(
                        PPS_CAPTURE_DB_ENTRY_EXCEPTION, payment.getMgmJourneyId(),
                        payment.getMgmCorrelationId(), payment.getMgmTransactionId(), payment.getRequestChannel(),
                        null, payment.getClientId(), "user-agent")
                );
            }
            else {
                logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_OPERATION, CAPTURE_CLASS_NAME, PPS_CAPTURE_DB_ENTRY_EXCEPTION,
                        payment.getRequestChannel(), payment.getMgmJourneyId(), payment.getMgmCorrelationId(),
                        payment.getMgmTransactionId(), payment.getClientId(), payment.getMgmId(), getSpanId(), getTraceId(), StatusResult.F.name(), StatusResult.F.getResult(), "",
                        e.getMessage());
                publishAuditData(new String[]{"", "", "FAILED_CAPTURE_DB_EXCEPTION", "Exception while updating the DB with the " +
                        "failed capture response", StatusResult.F.getResult(), StatusResult.F.name()}, PaymentRequest.builder().clientReferenceNumber(payment
                                .getClientReferenceNumber()).sessionId(payment.getSessionId()).mgmId(payment.getMgmId())
                        .build(), null, maskedResponse, payment, LocalDateTime.now(), new HeadersDTO(
                        PPS_CAPTURE_DB_ENTRY_EXCEPTION, payment.getMgmJourneyId(),
                        payment.getMgmCorrelationId(), payment.getMgmTransactionId(), payment.getRequestChannel(),
                        null, payment.getClientId(), "user-agent")
                );
            }
        }
    }

    /**
     * Maps the routerResponse to payment response and returns
     *
     * @param payment-        database record
     * @param paymentRequest- input request
     * @param prResponse-     router service response
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
                        .type(TransactionType.CAPTURE)
                        .groupId(payment.getGroupId())
                        .mgmId(routerGatewayResult.getMgmId()).amount(amount)
                        .card(Card.builder().maskedCardNumber(routerCardResponse.getTenderDisplay())
                                .gatewayId(routerCardResponse.getGatewayId())
                                .mgmToken(payment.getMgmToken())
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
                                .gatewayResponse(transactionResponse.getGatewayResponse())
                                .avsResult(transactionResponse.getAvsResult())
                                .avsValid(transactionResponse.getAvsValid())
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
     * Update database roe with status FAILED in case of exception while router-service call
     *
     * @param payment - db record
     * @param e - exception
     */
    public void updateDBOnFailure(PaymentEntity payment, HeadersDTO headersDTO, Exception e) {
        if (payment != null && (TransactionStatus.IN_PROCESS.equals(payment.getTransactionStatus()))) {
            capturePaymentRepositoryWrapper.deleteById(payment.getReferenceId(), headersDTO);
            PaymentProcessingUtil.getFailureEntity(payment, e);
            repository.save(payment, headersDTO);
        }
    }


    private void captureConfirmService(PaymentRequest paymentRequest, String sessionId, HeadersDTO headersDTO, String transactionCode,
                                       Transaction transaction) {

        String processorResponseText = PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE;
        String orderStatus = PaymentProcessingConstants.CANCELED_BY_MERCHANT;
        List<Amount> amount = paymentRequest.getAmount();
        String responseCode = transaction.getResponseCode();
        String  avsResult = transaction.getAvsResult();
        if (transactionCode.equals(TransactionStatus.SUCCESS.getStatusCode())) {
            processorResponseText = SUCCESS_RESPONSE_STATUS;
            orderStatus = PaymentProcessingConstants.COMPLETED;
        }
        CaptureConfirm captureConfirm = CaptureConfirm.builder()
                .mgmId(paymentRequest.getMgmId())
                .orderReferenceNumber(paymentRequest.getClientReferenceNumber())
                .sessionId(sessionId)
                .amount(amount)
                .orderStatus(orderStatus)
                .processorResponseText(processorResponseText)
                .responseCode(responseCode)
                .avsResult(avsResult).build();
        String masked = LogMaskingConverter.mask(captureConfirm);
        if (logger.isInfoEnabled()) {
            logger.info("Service : Event call to  ConfirmEvent executing.... :: captureConfirm : {}", masked);
        }
        this.captureConfirmEventService.onApplicationEvent(new ConfirmEvent(captureConfirm, headersDTO));

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
