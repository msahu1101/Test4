package com.mgm.payments.processing.service.model;

import com.mgm.payments.processing.service.enums.CardPresent;
import com.mgm.payments.processing.service.enums.CurrencyCode;
import com.mgm.payments.processing.service.enums.SecurityCodeIndicator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PRPayment {

    private BillingAddress billingAddress;
    private CurrencyCode currencyCode;
    private SecurityCodeIndicator securityCodeIndicator;
    private CardPresent cardPresent;
    private TenderDetails tenderDetails;
}
