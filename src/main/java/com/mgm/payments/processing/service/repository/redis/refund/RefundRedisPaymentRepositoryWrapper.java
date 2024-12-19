package com.mgm.payments.processing.service.repository.redis.refund;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.entity.redis.RefundRedisEntity;
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
public class RefundRedisPaymentRepositoryWrapper {
    private final Logger logger = LoggerFactory.getLogger(RefundRedisPaymentRepositoryWrapper.class);

    private final RefundRedisPaymentRepository redisPaymentRepository;
    private final Tracer tracer;
    @Autowired
    public RefundRedisPaymentRepositoryWrapper(RefundRedisPaymentRepository redisPaymentRepository, Tracer tracer){
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

    public RefundRedisEntity save(RefundRedisEntity payment, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        RefundRedisEntity refundRedisEntity = redisPaymentRepository.save(payment);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", REFUND_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Saved to refund Redis Cache !!");
        return refundRedisEntity;
    }

    public Optional<RefundRedisEntity> findById(String id, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Optional<RefundRedisEntity> refundRedisEntity = redisPaymentRepository.findById(id);
        stopWatch.stop();
        String logMessage = refundRedisEntity.isPresent() ? "Record Fetched from refund Redis Cache !!" : "Record Not Found in refund Redis Cache !!";
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findById", REFUND_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), logMessage);
        return refundRedisEntity;
    }

    public void deleteById(String paymentId, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        redisPaymentRepository.deleteById(paymentId);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "deleteById", REFUND_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Deleted from refund Redis Cache !!");
    }


}
