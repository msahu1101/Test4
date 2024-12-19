package com.mgm.payments.processing.service.repository.redis.auth;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.entity.redis.AuthorizeRedisEntity;
import com.mgm.payments.processing.service.model.HeadersDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Component
public class AuthorizeRedisPaymentRepositoryWrapper {

    private final Logger logger = LoggerFactory.getLogger(AuthorizeRedisPaymentRepositoryWrapper.class);

    private final AuthorizeRedisPaymentRepository redisPaymentRepository;
    private final Tracer tracer;
    @Autowired
    public AuthorizeRedisPaymentRepositoryWrapper(AuthorizeRedisPaymentRepository redisPaymentRepository, Tracer tracer) {
        this.redisPaymentRepository = redisPaymentRepository;
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

    public AuthorizeRedisEntity save(AuthorizeRedisEntity payment, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        AuthorizeRedisEntity authorizeRedisEntity = redisPaymentRepository.save(payment);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", AUTHORIZE_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Saved to authorize Redis Cache !!");
        return authorizeRedisEntity;
    }

    public Optional<AuthorizeRedisEntity> findById(String id, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Optional<AuthorizeRedisEntity> authorizeRedisEntity = redisPaymentRepository.findById(id);
        stopWatch.stop();
        String logMessage = authorizeRedisEntity.isPresent() ? "Record Fetched from authorize Redis Cache !!" : "Record Not Found in authorize Redis Cache !!";
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findById", AUTHORIZE_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), logMessage);
        return authorizeRedisEntity;
    }

    public void deleteById(String paymentId, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        redisPaymentRepository.deleteById(paymentId);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "deleteById", AUTHORIZE_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Deleted from authorize Redis Cache !!");
    }


}
