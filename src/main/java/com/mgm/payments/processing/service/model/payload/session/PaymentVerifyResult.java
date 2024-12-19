package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentVerifyResult {
    private String type;
    private String decision;
    private String responseCode;
    private String authorizationCode;
    private String authorizedDateTime;
    private String avsCode;
    private String cvCode;
    private String status;
    private String errorCode;
    private String errorDescription;
}
