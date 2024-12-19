package com.mgm.payments.processing.service.repository.redis.capture;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.entity.redis.CaptureRedisEntity;
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
public class CaptureRedisPaymentRepositoryWrapper {
    private final Logger logger = LoggerFactory.getLogger(CaptureRedisPaymentRepositoryWrapper.class);

    private final CaptureRedisPaymentRepository redisPaymentRepository;
    private final Tracer tracer;
    @Autowired
    public CaptureRedisPaymentRepositoryWrapper(CaptureRedisPaymentRepository redisPaymentRepository, Tracer tracer) {
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

    public CaptureRedisEntity save(CaptureRedisEntity payment, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        CaptureRedisEntity captureRedisEntity = redisPaymentRepository.save(payment);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", CAPTURE_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Saved to capture Redis Cache !!");
        return captureRedisEntity;
    }

    public Optional<CaptureRedisEntity> findById(String id, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Optional<CaptureRedisEntity> captureRedisEntity = redisPaymentRepository.findById(id);
        stopWatch.stop();
        String logMessage = captureRedisEntity.isPresent() ? "Record Fetched from capture Redis Cache !!" : "Record Not Found in capture Redis Cache !!";
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findById", CAPTURE_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), logMessage);
        return captureRedisEntity;
    }

    public void deleteById(String paymentId, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        redisPaymentRepository.deleteById(paymentId);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "deleteById", CAPTURE_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Deleted from capture Redis Cache !!");

    }


}
