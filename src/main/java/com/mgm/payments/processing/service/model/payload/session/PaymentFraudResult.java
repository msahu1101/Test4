package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentFraudResult {
    private String recommendationCode;
    private String fraudAgent;
    private String decision;
    private String reasonCode;
    private String status;
    private String errorCode;
    private String errorDescription;

}
