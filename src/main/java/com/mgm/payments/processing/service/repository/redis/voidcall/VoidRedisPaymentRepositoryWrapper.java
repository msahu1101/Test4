package com.mgm.payments.processing.service.repository.redis.voidcall;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.entity.redis.VoidRedisEntity;
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
public class VoidRedisPaymentRepositoryWrapper {
    private final Logger logger = LoggerFactory.getLogger(VoidRedisPaymentRepositoryWrapper.class);

    private final VoidRedisPaymentRepository redisPaymentRepository;
    private final Tracer tracer;
    @Autowired
    public VoidRedisPaymentRepositoryWrapper(VoidRedisPaymentRepository redisPaymentRepository, Tracer tracer){
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

    public VoidRedisEntity save(VoidRedisEntity payment, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        VoidRedisEntity voidRedisEntity = redisPaymentRepository.save(payment);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", VOID_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Saved to void Redis Cache !!");
        return voidRedisEntity;
    }

    public Optional<VoidRedisEntity> findById(String id, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Optional<VoidRedisEntity> voidRedisEntity = redisPaymentRepository.findById(id);
        stopWatch.stop();
        String message = voidRedisEntity.isPresent() ? "Record Fetched from void Redis Cache !!" : "Record Not Found in void Redis Cache !!";
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findById", VOID_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), message);
        return voidRedisEntity;
    }

    public void deleteById(String paymentId, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        redisPaymentRepository.deleteById(paymentId);
        stopWatch.stop();
        logger.info(PPS_REDIS_CACHE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "deleteById", VOID_REDIS_PAYMENT_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Deleted from void Redis Cache !!");
    }


}
