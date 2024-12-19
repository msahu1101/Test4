package com.mgm.payments.processing.service.external;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.enums.ApiErrorCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.ExternalServiceException;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.ServiceToken;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfigPayload;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Service
public class ClientConfigurationServiceCaller {
    private final Logger logger = LoggerFactory.getLogger(ClientConfigurationServiceCaller.class);

    private final WebClient webClient;
    private final PPSProperties ppsProperties;
    private final ServiceTokenCaller tokenCaller;
    private final Tracer tracer;

    @Autowired
    public ClientConfigurationServiceCaller(PPSProperties ppsProperties, WebClient webClient, ServiceTokenCaller tokenCaller, Tracer tracer) {
        this.ppsProperties = ppsProperties;
        this.webClient = webClient;
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

    public ClientConfigPayload getClientConfig(HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_CLIENT_CONFIG, CLIENT_CONFIG_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), headersDTO.getClientId());
        String url = ppsProperties.getClientConfigurationUrl()+headersDTO.getClientId();
        logger.info(PPS_REQUEST_URL_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_CLIENT_CONFIG, CLIENT_CONFIG_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), url);
        ServiceToken accessToken = tokenCaller.getServiceAccessToken(headersDTO);
        WebClient.ResponseSpec responseSpec = webClient.get().uri(url)
                .headers(httpHeaders -> {
                    httpHeaders.set(PaymentProcessingConstants.MGM_SOURCE, headersDTO.getMgmSource());
                    httpHeaders.set(PaymentProcessingConstants.JOURNEY_ID, headersDTO.getMgmJourneyId());
                    httpHeaders.set(PaymentProcessingConstants.CORRELATION_ID, headersDTO.getMgmCorrelationId());
                    httpHeaders.set(PaymentProcessingConstants.TRANSACTION_ID, headersDTO.getMgmTransactionId());
                    httpHeaders.set(PaymentProcessingConstants.CHANNEL, "WEB");
                    httpHeaders.setBearerAuth(accessToken.getAccess_token());
                }).retrieve()
                .onStatus(HttpStatus::isError, clientResponse -> getException(clientResponse, headersDTO));
        
        ResponseEntity<ClientConfigPayload> responseEntity = responseSpec.toEntity(ClientConfigPayload.class).block();
        if(responseEntity != null && responseEntity.getBody() != null){
            String maskedResponse = LogMaskingConverter.mask(responseEntity.getBody());
            stopWatch.stop();
            logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_CLIENT_CONFIG, CLIENT_CONFIG_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                    headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                    StatusResult.S.name(), StatusResult.S.getResult(), stopWatch.getTotalTimeMillis(), maskedResponse);
            return responseEntity.getBody();
        }
        String res = "Client Config is not available for the clientId : " + headersDTO.getClientId();
        logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_CLIENT_CONFIG, CLIENT_CONFIG_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                StatusResult.F.name(), StatusResult.F.getResult(), stopWatch.getTotalTimeMillis(), res);
        return null;
    }

    private Mono<ExternalServiceException> getException(ClientResponse clientResponse, HeadersDTO headersDTO) {
        return clientResponse.toEntity(String.class)
                .flatMap(e -> {
                    HttpStatus httpStatus = clientResponse.statusCode();
                    String errorType = "5XX";
                    if(httpStatus.is4xxClientError()) {
                        errorType = "4XX";
                    }
                    PaymentExceptionResponse response = PaymentExceptionResponse.builder()
                            .dateTime(ZonedDateTime.now())
                            .errorCode(ApiErrorCode.CLIENT_CONFIG_CALL_ERROR.getCode())
                            .errorMessage(ApiErrorCode.CLIENT_CONFIG_CALL_ERROR.getDescription())
                            .developerMessage(e.getBody() != null ? e.getBody() : e.toString())
                            .build();
                    logger.error(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_CLIENT_CONFIG + "-" + errorType, CLIENT_CONFIG_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                            headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                            headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                            StatusResult.E.name(), StatusResult.E.getResult(), "", response);
                    throw new PaymentProcessingException(response, httpStatus);
                }).switchIfEmpty(Mono.defer(() -> {
                    HttpStatus httpStatus = clientResponse.statusCode();
                    String e = httpStatus.toString();
                    String errorType = "5XX";
                    if(httpStatus.is4xxClientError()) {
                        errorType = "4XX";
                    }
                    PaymentExceptionResponse response = PaymentExceptionResponse.builder()
                            .dateTime(ZonedDateTime.now())
                            .errorCode(ApiErrorCode.CLIENT_CONFIG_CALL_ERROR.getCode())
                            .errorMessage(ApiErrorCode.CLIENT_CONFIG_CALL_ERROR.getDescription())
                            .originError(e)
                            .build();
                    logger.error(PPS_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_CLIENT_CONFIG + "-" + errorType, CLIENT_CONFIG_CLASS_NAME, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                            headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(),
                            headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                            StatusResult.E.name(), StatusResult.E.getResult(), "", response);
                    throw new PaymentProcessingException(response, httpStatus);
                })).cast(ExternalServiceException.class);
    }
}
