package com.mgm.payments.processing.service.repository.jpa;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.payload.PaymentEntityDTO;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.repository.jpa.primary.PrimaryRepository;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;
import static com.mgm.payments.processing.service.enums.ApiErrorCode.RETRY_EXCEEDS_ERROR;

@Component
@Retryable(value = {Exception.class},maxAttempts = MAX_RETRY,backoff = @Backoff(DELAY_SECONDS*1000))
public class PaymentProcessingRepositoryWrapper {
    private final Logger logger = LoggerFactory.getLogger(PaymentProcessingRepositoryWrapper.class);

    PrimaryRepository primaryRepository;
    Tracer tracer;
    @Qualifier("primaryEntityManagerFactory")
    private final EntityManager entityManager;

    @Autowired
    public PaymentProcessingRepositoryWrapper(PrimaryRepository primaryRepository, Tracer tracer, EntityManager entityManager) {
        this.primaryRepository = primaryRepository;
        this.tracer = tracer;
        this.entityManager = entityManager;
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


    public List<PaymentEntity> findByClientReferenceNumber(String clientReferenceNumber, HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<PaymentEntityDTO> paymentEntityDTOList = primaryRepository.findByClientReferenceNumber(clientReferenceNumber);
        String message = paymentEntityDTOList.isEmpty() ? "No Payment Transaction Details found in DB !!" : "Payment Transaction Details found in DB !!";
        List<PaymentEntity> paymentEntityList = PaymentProcessingUtil.mapToEntity(paymentEntityDTOList);
        stopWatch.stop();
        logger.info(PPS_DB_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findByClientReferenceNumber", PAYMENT_PROCESSING_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), message);
        return paymentEntityList;
    }


    public List<PaymentEntity> findByPaymentIdOrReferenceId(String paymentId, String parentId, HeadersDTO headersDTO){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        List<PaymentEntityDTO> paymentEntityDTOList = primaryRepository.findByPaymentIdOrReferenceId(paymentId, parentId);
        String message = paymentEntityDTOList.isEmpty() ? "No Payment Transaction Details found in DB !!" : "Payment Transaction Details found in DB !!";
        stopWatch.stop();
        List<PaymentEntity> paymentEntities = PaymentProcessingUtil.mapToEntity(paymentEntityDTOList);
        logger.info(PPS_DB_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findByPaymentIdOrReferenceId", PAYMENT_PROCESSING_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), stopWatch.getTotalTimeMillis(),
                message);
        return paymentEntities;
    }


    @Transactional
    public PaymentEntity save(PaymentEntity payment, HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        entityManager.persist(payment);
        PaymentEntity save = primaryRepository.save(payment);
        stopWatch.stop();
        logger.info(PPS_DB_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "save", PAYMENT_PROCESSING_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(),
                stopWatch.getTotalTimeMillis(), "Record Saved to DB !!");
        return save;
    }



    @Recover
    public List<PaymentEntity> recover(Exception e){
        if(e.getCause() instanceof JDBCConnectionException){
            String errorMsg = "Failed to connect to database after " + MAX_RETRY + " attempts : " + e.getCause();
            logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "DB RETRY", PAYMENT_PROCESSING_REPOSITORY_WRAPPER,
                    "", "", "", "", "", "", "", getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(), "",
                    errorMsg);
        } else {
            Throwable throwable = e.getCause() != null ? e.getCause() : e;
            String errorMsg = "Exception during DB operation" + throwable ;
            logger.info(PPS_DB_EXCEPTION_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "DB RETRY", PAYMENT_PROCESSING_REPOSITORY_WRAPPER,
                    "", "", "", "", "", "", "", getSpanId(), getTraceId(), StatusResult.E.name(), StatusResult.E.getResult(), "",
                    errorMsg);
        }
        PaymentExceptionResponse paymentExceptionResponse = new PaymentExceptionResponse();
        paymentExceptionResponse.setDateTime(ZonedDateTime.now());
        paymentExceptionResponse.setErrorCode(RETRY_EXCEEDS_ERROR.getCode());
        paymentExceptionResponse.setDeveloperMessage(RETRY_EXCEEDS_ERROR.getDescription());
        paymentExceptionResponse.setErrorMessage("Retry failed while executing DB Operation");
        paymentExceptionResponse.setOriginError(e.getMessage());
        throw new PaymentProcessingException(paymentExceptionResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public PaymentEntity findByPaymentId(String paymentId, HeadersDTO headersDTO) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Optional<PaymentEntity> paymentEntityOptional = primaryRepository.findById(paymentId);
        String message = paymentEntityOptional.isEmpty() ? "No Payment Transaction Details found in DB !!" : "Payment Transaction Details found in DB !!";
        stopWatch.stop();
        PaymentEntity paymentEntity = paymentEntityOptional.orElse(null);
        logger.info(PPS_DB_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, "findByPaymentId", PAYMENT_PROCESSING_REPOSITORY_WRAPPER, headersDTO.getMgmSource(), headersDTO.getMgmChannel(),
                headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(), headersDTO.getMgmTransactionId(), headersDTO.getClientId(), "", getSpanId(), getTraceId(), stopWatch.getTotalTimeMillis(),
                message);
        return paymentEntity;
    }

}
