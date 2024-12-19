package com.mgm.payments.processing.service.external;

import brave.Span;
import brave.Tracer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.enums.ApiErrorCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.ExternalServiceException;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Service
public class SessionServiceCaller {

    private final Logger logger = LoggerFactory.getLogger(SessionServiceCaller.class);

    private final WebClient webClient;
    private final PPSProperties ppsProperties;
    private final ServiceTokenCaller tokenCaller;
    private final Tracer tracer;

    @Autowired
    public SessionServiceCaller(WebClient webClient, PPSProperties ppsProperties, ServiceTokenCaller tokenCaller, Tracer tracer) {
        this.webClient = webClient;
        this.ppsProperties = ppsProperties;
        this.tokenCaller = tokenCaller;
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

    public PaymentSession retrieveSession(HeadersDTO headersDTO, String sessionId) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, SESSION_RETRIEVE_CALL, SESSION_CLASS_NAME,
                headersDTO.getMgmSource(), headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), sessionId);
        String url = ppsProperties.getSessionUrl() + SESSION_ENDPOINT + sessionId;
        logger.info(PPS_REQUEST_URL_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, SESSION_RETRIEVE_CALL, SESSION_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), url);

        String accessToken = tokenCaller.getServiceAccessToken(headersDTO).getAccess_token();

        WebClient.ResponseSpec retrieve = webClient.get().uri(url).headers(httpHeaders -> {
                    httpHeaders.set(MGM_SOURCE, PAM_SERVICE);
                    httpHeaders.set(JOURNEY_ID, headersDTO.getMgmJourneyId());
                    httpHeaders.set(CORRELATION_ID, headersDTO.getMgmCorrelationId());
                    httpHeaders.set(TRANSACTION_ID, headersDTO.getMgmTransactionId());
                    httpHeaders.set(CHANNEL, headersDTO.getMgmChannel());
                    httpHeaders.setBearerAuth(accessToken);
                    httpHeaders.set(CLIENT_ID, headersDTO.getClientId());
                }).retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                        getExceptionMono(clientResponse, headersDTO)
                ).onStatus(HttpStatus::is5xxServerError, clientResponse ->
                        getExceptionMono(clientResponse, headersDTO));
        ResponseEntity<PaymentSession> sessionResponseEntity = retrieve.toEntity(PaymentSession.class)
                .retryWhen(Retry.backoff(ppsProperties.getRetryCount(), Duration.ofSeconds(ppsProperties.getRetryDelay()))
                        .filter(ExternalServiceException.class::isInstance)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) -> {
                            String developerMessage = ApiErrorCode.RETRY_EXCEEDS_ERROR.getDescription();
                            String error = ((ExternalServiceException) retrySignal.failure()).getRouterError();
                            HttpStatus httpStatus = ((ExternalServiceException) retrySignal.failure()).getStatusCode();
                            PaymentExceptionResponse errorResponse = PaymentProcessingUtil.getErrorResponse(error, "",
                                    developerMessage);
                            throw new PaymentProcessingException(errorResponse, httpStatus);
                        }))
                ).block();
        assert sessionResponseEntity != null;
        PaymentSession paymentSession = sessionResponseEntity.getBody();
        String status = StatusResult.S.name();
        String result = StatusResult.S.getResult();
        String maskedResponse = LogMaskingConverter.mask(paymentSession);
        stopWatch.stop();
        logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, SESSION_RETRIEVE_CALL, SESSION_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(),
                "", getSpanId(), getTraceId(),
                status, result,  stopWatch.getTotalTimeMillis(), maskedResponse);
        return paymentSession;
    }

    @NotNull
    private Mono<ExternalServiceException> getExceptionMono(ClientResponse clientResponse, HeadersDTO headersDTO) {
        return clientResponse.
                bodyToMono(String.class)
                .flatMap(e -> {
                    HttpStatus httpStatus = clientResponse.statusCode();
                    String errorType = "5XX Error";
                    if(httpStatus.is4xxClientError()){
                        errorType = "4XX Error";
                    }
                    PaymentExceptionResponse errorResponse = getPaymentExceptionResponse(httpStatus, errorType, e, headersDTO);
                    throw new PaymentProcessingException(errorResponse, httpStatus);
                }).switchIfEmpty(Mono.defer(() -> {
                    HttpStatus httpStatus = clientResponse.statusCode();
                    String errorType = "5XX Error";
                    if(httpStatus.is4xxClientError()){
                        errorType = "4XX Error";
                    }
                    String e = httpStatus.toString();
                    PaymentExceptionResponse errorResponse = getPaymentExceptionResponse(httpStatus, errorType, e, headersDTO);
                    throw new PaymentProcessingException(errorResponse, httpStatus);
                })).cast(ExternalServiceException.class);
    }

    private PaymentExceptionResponse getPaymentExceptionResponse(HttpStatus httpStatus, String statusCode, String e, HeadersDTO headersDTO) {
        String methodName = " Retrieve Session Call - " + statusCode + "!!";
        String mgmId = "";
        if (isRetryErrorCode(httpStatus.value(), e)) {
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, SESSION_RETRIEVE_CALL, SESSION_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                    headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                    StatusResult.E.name(), StatusResult.E.getResult(), "", e);
            throw new ExternalServiceException(e, httpStatus);
        }

        PaymentExceptionResponse errorResponse = PaymentProcessingUtil.getErrorResponse(e, "", ApiErrorCode.PAYMENT_SESSION_EXCEPTION.getDescription() +
                methodName + httpStatus);
        logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, SESSION_RETRIEVE_CALL, SESSION_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                StatusResult.E.name(), StatusResult.E.getResult(), "", errorResponse);
        return errorResponse;
    }

    //        408 - Request TimeOut,
    //        425 - Too Early,
    //        429 - Too Many Requests,
    //        500 - Internal Server Error,
    //        502 -  Bad Gateway,
    //        503 - Service Unavailable,
    //        504  - Gateway Timeout
    private boolean isRetryErrorCode(Integer value, String error) {
        List<Integer> retryErrorCode = Arrays.asList(408, 425, 429, 500, 502, 503, 504);
        boolean isRetryNeeded = retryErrorCode.stream().anyMatch(code ->
                code.equals(value));
        try{
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, Object> routerException = new ObjectMapper().readValue(error, LinkedHashMap.class);
            isRetryNeeded  = isRetryNeeded && (routerException.get("errorCode") == null ||
                    routerException.get("errorCode").toString().endsWith("1") &&
                            value != 504);
        }catch(JsonProcessingException e){
            logger.error("Exception while reading errorMessage : {}",error, e);
        }
        return isRetryNeeded;
    }

}
