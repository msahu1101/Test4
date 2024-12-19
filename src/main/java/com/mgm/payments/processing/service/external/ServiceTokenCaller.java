package com.mgm.payments.processing.service.external;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.config.PPSConfig;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.enums.ApiErrorCode;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.ServiceToken;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.util.ServiceTokenCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Service
public class ServiceTokenCaller {


    private final Logger logger = LoggerFactory.getLogger(ServiceTokenCaller.class);

    private final WebClient webClient;
    private final PPSProperties ppsProperties;
    private final Tracer tracer;

    @Autowired
    public ServiceTokenCaller(WebClient webClient, PPSProperties ppsProperties, Tracer tracer) {
        this.webClient = webClient;
        this.ppsProperties = ppsProperties;
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
     * Gets Access Token to invoke other microservice
     *
     * @return ServiceToken
     */
    public ServiceToken getServiceAccessToken(HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_SERVICE_ACCESS_TOKEN, SERVICE_TOKEN_CALLER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), "Start of getServiceAccessToken method");

        String token = ServiceTokenCache.getServiceToken();
        if (token != null) {
            stopWatch.stop();
            logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_SERVICE_ACCESS_TOKEN, SERVICE_TOKEN_CALLER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                    StatusResult.S.name(), StatusResult.S.getResult(), stopWatch.getTotalTimeMillis(), "returning service token from cache");
            ServiceToken serviceToken = new ServiceToken();
            serviceToken.setAccess_token(token);
            return serviceToken;
        }

        try {

            PPSConfig ppsConfig = ppsProperties.getPPSConfig();
            String url = ppsProperties.getServiceTokenHost() + ppsProperties.getServiceTokenUri();
            logger.info(PPS_REQUEST_URL_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_SERVICE_ACCESS_TOKEN, SERVICE_TOKEN_CALLER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), url);
            WebClient.ResponseSpec responseSpec = webClient.post().uri(url)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(BodyInserters
                            .fromFormData(PaymentProcessingConstants.SERVICE_TOKEN_CLIENT_ID,
                                    ppsConfig.getPdServiceId())
                            .with(PaymentProcessingConstants.SERVICE_TOKEN_CLIENT_SECRET,
                                    ppsConfig.getPdServicePassword())
                            .with(PaymentProcessingConstants.SERVICE_TOKEN_GRANT_TYPE,
                                    ppsProperties.getServiceTokenGrantType()))
                    .retrieve()
                    .onStatus(HttpStatus::is4xxClientError,
                            error -> error.bodyToMono(String.class).flatMap(errorBody -> {
                                logger.error("External Service: ServiceTokenCaller :: 4xxClientError :: {}", errorBody);
                                PaymentExceptionResponse response = PaymentExceptionResponse.builder()
                                        .dateTime(ZonedDateTime.now())
                                        .errorCode(ApiErrorCode.BAD_REQUEST_EXCEPTION.getCode())
                                        .errorMessage(ApiErrorCode.BAD_REQUEST_EXCEPTION.getDescription() + PaymentProcessingConstants.WHILE_ACCESS_TOKEN_CALL)
                                        .developerMessage(errorBody)
                                        .build();
                                throw new PaymentProcessingException(response, HttpStatus.BAD_REQUEST);
                            }))
                    .onStatus(HttpStatus::is5xxServerError, error -> error.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                logger.error("External Service: ServiceTokenCaller :: 5xxServerError :: {}", errorBody);
                                PaymentExceptionResponse response = PaymentExceptionResponse.builder()
                                        .dateTime(ZonedDateTime.now())
                                        .errorCode(ApiErrorCode.INTERNAL_SERVER_ERROR.getCode())
                                        .errorMessage(ApiErrorCode.INTERNAL_SERVER_ERROR.getDescription() + PaymentProcessingConstants.WHILE_ACCESS_TOKEN_CALL)
                                        .developerMessage(errorBody)
                                        .build();
                                throw new PaymentProcessingException(response, HttpStatus.INTERNAL_SERVER_ERROR);
                            })
                    );

            ResponseEntity<ServiceToken> response = responseSpec.toEntity(ServiceToken.class).block();
            ServiceToken serviceToken = response != null ? response.getBody() : null;
            if(serviceToken != null){
                String serviceTok=serviceToken.getAccess_token();
                ServiceTokenCache.setServiceToken(serviceTok);
                ServiceTokenCache.setServiceTokenExpireTime(Instant.now().plusSeconds(serviceToken.getExpires_in()));
                stopWatch.stop();
                logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, GET_SERVICE_ACCESS_TOKEN, SERVICE_TOKEN_CALLER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                        headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "",
                        getSpanId(), getTraceId(), StatusResult.S.name(), StatusResult.S.getResult(), stopWatch.getTotalTimeMillis(), "new service token generated and stored in cache !!");
            }
            return serviceToken;

        } catch (WebClientRequestException exception) {
            logger.error("External Service: ServiceTokenCaller :: WebClientRequestException : {}", exception.toString());
            PaymentExceptionResponse response = PaymentExceptionResponse.builder()
                    .dateTime(ZonedDateTime.now())
                    .errorCode(ApiErrorCode.INTERNAL_SERVER_ERROR.getCode())
                    .errorMessage(ApiErrorCode.INTERNAL_SERVER_ERROR.getDescription() + PaymentProcessingConstants.WHILE_ACCESS_TOKEN_CALL)
                    .developerMessage(exception.getMessage())
                    .build();
            throw new PaymentProcessingException(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
}