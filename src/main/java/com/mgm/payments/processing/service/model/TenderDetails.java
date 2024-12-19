package com.mgm.payments.processing.service.model;


import com.mgm.payments.processing.service.validation.group.AdhocRefundGroup;
import com.mgm.payments.processing.service.validation.group.AuthorizeGroup;
import com.mgm.payments.processing.service.validation.group.RefundGroup;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;


@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Builder
public class TenderDetails {

    @NotBlank(groups = {AuthorizeGroup.class})
    private String nameOnTender;

    @NotBlank(groups = {AuthorizeGroup.class})
    private String maskedCardNumber;

    @NotNull(groups = {AuthorizeGroup.class})
    @Pattern(regexp="^(Credit Card|Debit Card|Gift Card)$",message="Invalid value for { tenderType }, allowed values are Credit Card,Debit Card,Gift Card")
    private String tenderType;

    @NotNull(groups = {AuthorizeGroup.class})
    @Pattern(regexp="^(Visa|Mastercard|Discover|American Express|Diners Club|JCB|Union Pay|Gift Card)$",message="Invalid value for { issuerType }, allowed values are Visa, Mastercard, Discover, American Express, Diners Club, JCB, Union Pay, Gift Card")
    private String issuerType;

    @NotBlank(groups = {AuthorizeGroup.class, RefundGroup.class, AdhocRefundGroup.class})
    private String mgmToken;

    @NotNull(groups = {AuthorizeGroup.class})
    private String expireMonth;

    @NotNull(groups = {AuthorizeGroup.class})
    private String expireYear;

    private String payerId;

    private String paymentMethodNonce;

    private String deviceData;

    private String securityCode;

}
