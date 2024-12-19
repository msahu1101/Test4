package com.mgm.payments.processing.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeadersDTO {
    private String mgmSource;
    private String mgmJourneyId;
    private String mgmCorrelationId;
    private String mgmTransactionId;
    private String mgmChannel;
    private String authorization;
    private String clientId;
    private String userAgent;
}
