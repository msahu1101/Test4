package com.mgm.payments.processing.service.model.payload;

import com.mgm.payments.processing.service.enums.CurrencyCode;
import com.mgm.payments.processing.service.enums.TransactionStatus;
import com.mgm.payments.processing.service.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentEntityDTO {
    private String paymentId;
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    @Enumerated(EnumType.STRING)
    private TransactionStatus transactionStatus;
    private BigDecimal amount;
    private BigDecimal authorizedAmount;
    private String mgmToken;
    private String clientReferenceNumber;
    private String referenceId;
    private String sessionId;
    private String mgmId;
    private String gatewayChainId;
    private String groupId;
    private String gatewayId;
    private String clientId;
    private String orderType;
    private String cardHolderName;
    private String tenderType;
    private String cardEntryMode;
    private String last4DigitsOfTheCard;
    private String issuerType;
    private CurrencyCode currencyCode;
    private String billingAddress1;
    private String billingAddress2;
    private String billingCity;
    private String billingCountry;
    private String billingState;
    private String billingZipcode;
    private String createdTimestamp;
    private String requestChannel;

    // constructor
    public PaymentEntityDTO(String paymentId, TransactionType transactionType, TransactionStatus transactionStatus, BigDecimal amount,
                            BigDecimal authorizedAmount, String mgmToken, String clientReferenceNumber, String referenceId, String clientId,
                            String createdTimestamp, String last4DigitsOfTheCard, String requestChannel) {
        this.paymentId = paymentId;
        this.transactionType = transactionType;
        this.transactionStatus = transactionStatus;
        this.amount = amount;
        this.authorizedAmount = authorizedAmount;
        this.mgmToken = mgmToken;
        this.clientReferenceNumber = clientReferenceNumber;
        this.referenceId = referenceId;
        this.clientId = clientId;
        this.createdTimestamp = createdTimestamp;
        this.last4DigitsOfTheCard = last4DigitsOfTheCard;
        this.requestChannel = requestChannel;
    }
}
