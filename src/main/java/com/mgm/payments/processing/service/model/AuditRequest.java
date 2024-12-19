package com.mgm.payments.processing.service.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
public class AuditRequest {
    private String id;
    private String topic;
    private String subject;
    private String eventType;
    private String eventTime;
    private String clientReferenceNumber;
    private String sessionId;
    private String mgmId;
    private String clientId;
    private String journeyId;
    private String executionId;
    private String correlationId;
    private String transactionId;
    private String requestId;
    private String gatewayChainId;
    private String mgmChannel;
    private LocalDateTime createdDate;
    private String dataVersion;
    private String cardEntryMode;
    private AuditData auditData;
}
