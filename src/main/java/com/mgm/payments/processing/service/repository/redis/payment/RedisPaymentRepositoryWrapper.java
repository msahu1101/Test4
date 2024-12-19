package com.mgm.payments.processing.service.repository.redis.payment;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.entity.redis.PaymentRedisEntity;
import com.mgm.payments.processing.service.model.HeadersDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Component
public class RedisPaymentRepositoryWrapper {
    private final Logger logger = LoggerFactory.getLogger(RedisPaymentRepositoryWrapper.class);

    private final RedisPaymentRepository redisPaymentRepository;
    private final Tracer tracer;
    private final PPSProperties ppsProperties;

    public RedisPaymentRepositoryWrapper(RedisPaymentRepository redisPaymentRepository, Tracer tracer, PPSProperties ppsProperties) {
        this.redisPaymentRepository = redisPaymentRepository;
        this.tracer = tracer;
        this.ppsProperties = ppsProperties;
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

    public PaymentRedisEntity save(PaymentRedisEntity payment, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        if (Boolean.TRUE.equals(ppsProperties.getReadFromCache())) {
            PaymentRedisEntity paymentRedisEntity = redisPaymentRepository.save(payment);
            stopWatch.stop();
            logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                    stopWatch.getTotalTimeMillis(), "Auth Record Saved to payment Redis Cache !!");
            return paymentRedisEntity;
        }else {
            stopWatch.stop();
            logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                    stopWatch.getTotalTimeMillis(), "Payment Redis Cache is disabled !! Not Saving Auth Record to Redis Cache !!");
            return null;
        }
    }

    public Optional<PaymentRedisEntity> findById(String id, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        if(Boolean.TRUE.equals(ppsProperties.getReadFromCache())) {
            Optional<PaymentRedisEntity> paymentRedisEntity = redisPaymentRepository.findById(id);
            stopWatch.stop();
            String logMessage = paymentRedisEntity.isPresent() ? "Auth Record Fetched from payment Redis Cache !!" : "Auth Record Not Found in payment Redis Cache !!";
            logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findById", REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                    stopWatch.getTotalTimeMillis(), logMessage);
            return paymentRedisEntity;
        }else {
            stopWatch.stop();
            logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findById", REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                    headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                    stopWatch.getTotalTimeMillis(), "Payment Redis Cache is disabled !! Not Fetching Auth Record from Redis Cache !!");
            return Optional.empty();
        }
    }

}
