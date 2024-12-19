package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAuthResult {
    private String type;
    private String authorizedAmount;
    private String decision;
    private String responseCode;
    private String paymentId;
    private String authorizationCode;
    private String authorizedDateTime;
    private String avsCode;
    private String cvCode;
    private String reconciliationId;
    private String remainingAuthAmount;
    private String errorCode;
    private String status;
    private String errorDescription;
}
