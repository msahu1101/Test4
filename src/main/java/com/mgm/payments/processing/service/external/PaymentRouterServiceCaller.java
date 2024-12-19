package com.mgm.payments.processing.service.external;

import brave.Span;
import brave.Tracer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.enums.ApiErrorCode;
import com.mgm.payments.processing.service.enums.RouterResponseCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.exception.ExternalServiceException;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.ServiceToken;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterRequest;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
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
public class PaymentRouterServiceCaller {

    private final Logger logger = LoggerFactory.getLogger(PaymentRouterServiceCaller.class);

    private final WebClient webClient;
    private final PPSProperties ppsProperties;
    private final ServiceTokenCaller tokenCaller;
    private final Tracer tracer;

    @Autowired
    public PaymentRouterServiceCaller(WebClient webClient, PPSProperties ppsProperties, ServiceTokenCaller tokenCaller, Tracer tracer) {
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


    /**
     * Invoke External Call to Payments Router Service
     *
     * @param prRequest   - PaymentRouter Request
     * @param headersDTO- Headers Parameter
     * @param paymentId-  paymentId of the transaction
     * @return RouterResponse from PaymentRouter Microservice
     * @throws PaymentProcessingException- thrown for 4XXClientError and 5XXServerError
     */
    public Mono<ResponseEntity<PaymentRouterResponse>> invokeRouter(PaymentRouterRequest prRequest, HeadersDTO headersDTO, String paymentId) throws PaymentProcessingException {
        String maskedRequest = LogMaskingConverter.mask(prRequest);
        String methodName = prRequest.getRouterFunction() + " Router Call";
        String mgmId = prRequest.getMgmId() != null ? prRequest.getMgmId() : "";
        logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, methodName, EXTERNAL_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), maskedRequest);

        long l = System.currentTimeMillis();
        ServiceToken serviceAccessToken = tokenCaller.getServiceAccessToken(headersDTO);
        String url = ppsProperties.getPaymentRouterUrl() + PaymentProcessingConstants.ROUTER_URL;
        logger.info(PPS_REQUEST_URL_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, methodName, EXTERNAL_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), url);
        WebClient.ResponseSpec responseSpec = webClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    httpHeaders.set(PaymentProcessingConstants.MGM_SOURCE, headersDTO.getMgmSource());
                    httpHeaders.set(PaymentProcessingConstants.JOURNEY_ID, headersDTO.getMgmJourneyId());
                    httpHeaders.set(PaymentProcessingConstants.CORRELATION_ID, headersDTO.getMgmCorrelationId());
                    httpHeaders.set(PaymentProcessingConstants.TRANSACTION_ID, headersDTO.getMgmTransactionId());
                    httpHeaders.set(PaymentProcessingConstants.CHANNEL, headersDTO.getMgmChannel());
                    httpHeaders.set(PaymentProcessingConstants.CLIENT_ID, headersDTO.getClientId());
                    httpHeaders.setBearerAuth(serviceAccessToken.getAccess_token());
                    httpHeaders.set(PaymentProcessingConstants.USER_AGENT, headersDTO.getUserAgent());
                })
                .body(BodyInserters.fromValue(prRequest)).retrieve()
                .onStatus(HttpStatus::is4xxClientError, clientResponse ->
                        getExceptionMono(prRequest, paymentId, clientResponse, headersDTO)
                )
                .onStatus(HttpStatus::is5xxServerError, clientResponse ->
                        getExceptionMono(prRequest, paymentId, clientResponse, headersDTO)
                );
        Mono<ResponseEntity<PaymentRouterResponse>> response = responseSpec.toEntity(PaymentRouterResponse.class)
                .retryWhen(Retry.backoff(ppsProperties.getRetryCount(), Duration.ofSeconds(ppsProperties.getRetryDelay()))
                        .filter(ExternalServiceException.class::isInstance)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) -> {
                            String developerMessage = prRequest.getRouterFunction().name() + ": " + ApiErrorCode.RETRY_EXCEEDS_ERROR.getDescription();
                            String error = ((ExternalServiceException) retrySignal.failure()).getRouterError();
                            HttpStatus httpStatus = ((ExternalServiceException) retrySignal.failure()).getStatusCode();
                            PaymentExceptionResponse errorResponse = PaymentProcessingUtil.getErrorResponse(error, paymentId,
                                    developerMessage);
                            throw new PaymentProcessingException(errorResponse, httpStatus);
                        }))
                );

        return response.map(prResponse -> {
            PaymentRouterResponse paymentRouterResponse = prResponse.getBody();
            assert paymentRouterResponse != null;
            String responseCode = paymentRouterResponse.getResults().get(0).getGatewayResult().getTransaction().getResponseCode();
            String status = StatusResult.F.name();
            String result = StatusResult.F.getResult();
            if(responseCode.equals(RouterResponseCode.A.toString())){
                status = StatusResult.S.name();
                result = StatusResult.S.getResult();
            }
            String maskedResponse = LogMaskingConverter.mask(paymentRouterResponse);
            logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, methodName, EXTERNAL_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                    headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                    status, result,  System.currentTimeMillis()-l, maskedResponse);
            return prResponse;
        });

    }

    @NotNull
    private Mono<ExternalServiceException> getExceptionMono(PaymentRouterRequest prRequest, String paymentId, ClientResponse clientResponse, HeadersDTO headersDTO) {
        return clientResponse.
                bodyToMono(String.class)
                .flatMap(e -> {
                    HttpStatus httpStatus = clientResponse.statusCode();
                    String errorType = "5XX Error";
                    if(httpStatus.is4xxClientError()){
                        errorType = "4XX Error";
                    }
                    PaymentExceptionResponse errorResponse = getPaymentExceptionResponse(httpStatus, errorType, e, paymentId, prRequest,
                            headersDTO);
                    throw new PaymentProcessingException(errorResponse, httpStatus);
                }).switchIfEmpty(Mono.defer(() -> {
                    HttpStatus httpStatus = clientResponse.statusCode();
                    String errorType = "5XX Error";
                    if(httpStatus.is4xxClientError()){
                        errorType = "4XX Error";
                    }
                    String e = httpStatus.toString();
                    PaymentExceptionResponse errorResponse = getPaymentExceptionResponse(httpStatus, errorType, e, paymentId, prRequest,
                            headersDTO);
                    throw new PaymentProcessingException(errorResponse, httpStatus);
                })).cast(ExternalServiceException.class);
    }

    private PaymentExceptionResponse getPaymentExceptionResponse(HttpStatus httpStatus, String statusCode, String e, String paymentId, PaymentRouterRequest prRequest, HeadersDTO headersDTO) {
        String methodName = prRequest.getRouterFunction() + " Router Call - " + statusCode + "!!";
        String mgmId = prRequest.getMgmId() != null ? prRequest.getMgmId() : "";
        if (isRetryErrorCode(httpStatus.value(), e)) {
            logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, methodName, EXTERNAL_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                    headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                    StatusResult.E.name(), StatusResult.E.getResult(), "", e);
            throw new ExternalServiceException(e, httpStatus);
        }

        PaymentExceptionResponse errorResponse = PaymentProcessingUtil.getErrorResponse(e, paymentId, ApiErrorCode.PAYMENT_ROUTER_EXCEPTION.getDescription() +
                prRequest.getRouterFunction().toString() + "!! " + httpStatus);
        logger.info(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, methodName, EXTERNAL_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
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
