package com.mgm.payments.processing.service.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditData {
    private String eventName;
    private String eventDescription;
    private String status;
    private String result;
    private LocalDateTime startTimeTS;
    private LocalDateTime endTimeTS;
    private String serviceName;
    private String mgmErrorCode;
    private String externalErrorCode;
    private String errorDescription;
    private String subject;
    private LocalDateTime createdDate;
    private Object requestPayload;
    private Object responsePayload;
    private String gateWayId;
    private String lastFour;
    private String mgmToken;
    private String processorToken;

}