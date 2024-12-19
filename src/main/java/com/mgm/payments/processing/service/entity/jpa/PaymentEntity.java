package com.mgm.payments.processing.service.entity.jpa;

import com.mgm.payments.processing.service.enums.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@EqualsAndHashCode
@Table(name = "t_payment")
public class PaymentEntity {


    @Id
    @Column(updatable = false,
            nullable = false)
    private String paymentId;

    private String groupId;
    private String gatewayChainId;
    private String clientReferenceNumber;
    private String referenceId;
    private BigDecimal amount;
    private BigDecimal authorizedAmount;
    private Integer authChainId;
    private String gatewayId;
    private String clientId;
    private String orderType;
    private String mgmId;
    private String mgmToken;
    private String cardHolderName;
    private String tenderType;
    private String tenderCategory;
    private String issuerType;
    @Enumerated(EnumType.STRING)
    private CurrencyCode currencyCode; //
    private String last4DigitsOfTheCard;
    @Column(name = "BILLING_ADDRESS_1")
    private String billingAddress1;
    @Column(name = "BILLING_ADDRESS_2")
    private String billingAddress2;
    private String billingCity;
    private String billingState;
    private String billingZipcode;
    private String billingCountry;
    private String paymentAuthId;
    private String clerkId;
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    @Enumerated(EnumType.STRING)
    private TransactionStatus transactionStatus;
    private String gatewayTransactionStatusCode;
    private String gatewayTransactionStatusReason;
    private String gatewayResponseCode;
    private String gatewayRrn;
    private String paymentAuthSource;
    private String deferredAuth;
    private String createdTimestamp;
    private String updatedTimestamp;
    private String createdBy;
    private String updatedBy;
    private String mgmCorrelationId;
    private String mgmJourneyId;
    private String mgmTransactionId;
    private String requestChannel;
    private String sessionId;
    private String cardEntryMode;
    private String avsResponseCode;
    private String cvvResponseCode;
    private String dccFlag;
    private String dccControlNumber;
    private String dccAmount;
    private String dccBinRate;
    private String dccBinCurrency;
    private String processorStatusCode;
    private String processorStatusMessage;
    private String processorAuthCode;
    private String authSubtype;

}
