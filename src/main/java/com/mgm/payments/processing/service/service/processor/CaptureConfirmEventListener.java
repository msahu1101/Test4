package com.mgm.payments.processing.service.service.processor;

import brave.Span;
import brave.Tracer;
import com.mgm.payments.processing.service.enums.StatusResult;
import com.mgm.payments.processing.service.events.ConfirmEvent;
import com.mgm.payments.processing.service.external.PaymentAuthManagerCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.AuditData;
import com.mgm.payments.processing.service.model.CaptureConfirm;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.util.LogMaskingConverter;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import reactor.core.publisher.Mono;


import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Component
public class CaptureConfirmEventListener {

    private final Logger logger = LoggerFactory.getLogger(CaptureConfirmEventListener.class);
    private final PaymentAuthManagerCaller paymentAuthManagerCaller;
    private final AuditMapper auditMapper;
    Tracer tracer;

    @Autowired
    public CaptureConfirmEventListener(PaymentAuthManagerCaller paymentAuthManagerCaller, AuditMapper auditMapper, Tracer tracer) {
        this.paymentAuthManagerCaller = paymentAuthManagerCaller;
        this.auditMapper = auditMapper;
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

    @Async
    public void onApplicationEvent(@NotNull ConfirmEvent confirmEvent) {
        CaptureConfirm captureConfirm = confirmEvent.getCaptureConfirm();
        HeadersDTO headersDTO = confirmEvent.getHeadersDTO();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        String mgmId = captureConfirm.getMgmId() !=null ? captureConfirm.getMgmId() : "";
        logger.info("Service : captureConfirmService execution started for orderReferenceNumber : {} ", captureConfirm.getOrderReferenceNumber());
        logger.info(PPS_REQUEST_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_CONFIRM_OPERATION, CAPTURE_CONFIRM_CLASS_NAME, headersDTO.getMgmSource(),
                headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), captureConfirm);
        Mono<String> monoResponse = paymentAuthManagerCaller.invokeCaptureConfirm(captureConfirm, headersDTO);
        stopWatch.stop();
        monoResponse.map(response ->
        {
            String maskedResponse = LogMaskingConverter.mask(response);
            logger.info(PPS_RESPONSE_LOG_FORMAT, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, CAPTURE_CONFIRM_OPERATION, CAPTURE_CONFIRM_CLASS_NAME, headersDTO.getMgmSource(),
                    headersDTO.getMgmChannel(), headersDTO.getMgmJourneyId(), headersDTO.getMgmCorrelationId(),
                    headersDTO.getMgmTransactionId(), headersDTO.getClientId(), mgmId, getSpanId(), getTraceId(), StatusResult.S.name(), StatusResult.S.getResult(), stopWatch.getTotalTimeMillis(),
                    maskedResponse);
            AuditData auditData = PaymentProcessingUtil.buildAuditData("CAPTURE_CONFIRM", "Capture Confirm External Call", "","",
                    captureConfirm, maskedResponse, null);
            auditData.setResult(StatusResult.S.getResult());
            auditData.setStatus(StatusResult.S.name());
            auditMapper.createAndPublishAuditTrailForRequestResponse(
                    captureConfirm.getOrderReferenceNumber(),
                    null, "", captureConfirm.getMgmId(),
                    "", headersDTO, auditData);
            return response;
        }).subscribe();

    }

}
