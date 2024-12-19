package com.mgm.payments.processing.service.model;

import com.mgm.payments.processing.service.validation.group.AdhocRefundGroup;
import com.mgm.payments.processing.service.validation.group.AuthorizeGroup;
import com.mgm.payments.processing.service.validation.group.RefundGroup;
import com.mgm.payments.processing.service.enums.CardPresent;
import com.mgm.payments.processing.service.enums.CurrencyCode;
import com.mgm.payments.processing.service.enums.SecurityCodeIndicator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payment {


    @NotNull(groups = {AuthorizeGroup.class})
    @Valid
    private BillingAddress billingAddress;

    @NotNull(groups = {AuthorizeGroup.class})
    private CurrencyCode currencyCode;

    @NotNull(groups = {AuthorizeGroup.class})
    private SecurityCodeIndicator securityCodeIndicator;

    @NotNull(groups = {AuthorizeGroup.class, RefundGroup.class, AdhocRefundGroup.class})
    private CardPresent cardPresent;

    @NotNull(groups = {AuthorizeGroup.class, RefundGroup.class, AdhocRefundGroup.class})
    @Valid
    private TenderDetails tenderDetails;

}
