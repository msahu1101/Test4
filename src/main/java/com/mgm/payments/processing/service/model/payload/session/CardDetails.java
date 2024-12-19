package com.mgm.payments.processing.service.model.payload.session;

import com.mgm.payments.processing.service.model.BillingAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardDetails {
    private String mgmToken;
    private String gatewayId;
    private String creditCardHolderName;
    private String tenderType;
    private String issuerType;
    private String tenderDisplay;
    private String expiryMonth;
    private String expiryYear;
    private BillingAddress billingAddress;
}
