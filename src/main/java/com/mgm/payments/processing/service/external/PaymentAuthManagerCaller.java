package com.mgm.payments.processing.service.external;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.enums.ApiErrorCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.exception.ExternalServiceException;
import com.mgm.payments.processing.service.model.CaptureConfirm;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.ServiceToken;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Service
public class PaymentAuthManagerCaller {
    private final Logger logger = LoggerFactory.getLogger(PaymentAuthManagerCaller.class);

    private final WebClient webClient;
    private final PPSProperties ppsProperties;
    private final ServiceTokenCaller tokenCaller;
    private final Tracer tracer;

    public PaymentAuthManagerCaller(WebClient webClient, PPSProperties ppsProperties, ServiceTokenCaller tokenCaller, Tracer tracer) {
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

    public Mono<String> invokeCaptureConfirm(CaptureConfirm request, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String mgmId = request.getMgmId() != null ? request.getMgmId() : "";
        logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, INVOKE_CAPTURE_CONFIRM, PAM_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), request);
        String url = ppsProperties.getPamUrl() + PaymentProcessingConstants.PAM_URL;
        logger.info(PPS_REQUEST_URL_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, INVOKE_CAPTURE_CONFIRM, PAM_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), url);
        ServiceToken serviceAccessToken = tokenCaller.getServiceAccessToken(headersDTO);
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
                .body(BodyInserters.fromValue(request)).retrieve()
                .onStatus(httpStatus -> getRetryErrorCode(httpStatus.value()), error ->
                        error.bodyToMono(String.class)
                                .flatMap(e -> {
                                    throw new ExternalServiceException(e, error.statusCode());
                                }))
                .onStatus(HttpStatus::isError, error -> error.bodyToMono(String.class)
                        .flatMap(e -> {
                            PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                                    .errorMessage(ApiErrorCode.PAM_CVS_EXCEPTION.getDescription())
                                    .errorCode(ApiErrorCode.PAM_CVS_EXCEPTION.getCode())
                                    .dateTime(ZonedDateTime.now())
                                    .developerMessage(e)
                                    .build();
                            throw new PaymentProcessingException(errorResponse, error.statusCode());
                        })

                );
        Mono<String> response = responseSpec.bodyToMono(String.class)
                .retryWhen(Retry.backoff(ppsProperties.getRetryCount(), Duration.ofSeconds(ppsProperties.getRetryDelay()))
                        .filter(ExternalServiceException.class::isInstance)
                        .onRetryExhaustedThrow(((retryBackoffSpec, retrySignal) -> {
                            String errorMessage = ApiErrorCode.RETRY_EXCEEDS_ERROR.getDescription();
                            String errorCode = ApiErrorCode.RETRY_EXCEEDS_ERROR.getCode();
                            String error = ((ExternalServiceException) retrySignal.failure()).getRouterError();
                            HttpStatus httpStatus = ((ExternalServiceException) retrySignal.failure()).getStatusCode();
                            PaymentExceptionResponse errorResponse = PaymentExceptionResponse.builder()
                                    .errorMessage(errorMessage)
                                    .errorCode(errorCode)
                                    .dateTime(ZonedDateTime.now())
                                    .developerMessage(error)
                                    .build();
                            throw new PaymentProcessingException(errorResponse, httpStatus);
                        }))
                );
        stopWatch.stop();
        logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, INVOKE_CAPTURE_CONFIRM, PAM_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(),
                StatusResult.S.name(), StatusResult.S.getResult(), stopWatch.getTotalTimeMillis(), response);
        return response;
    }
    private boolean getRetryErrorCode(Integer value) {
        List<Integer> retryErrorCode = Arrays.asList(408, 425, 429, 500, 502, 503, 504);
        return retryErrorCode.stream().anyMatch(code ->
                code.equals(value)
        );
    }
}
