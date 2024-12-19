package com.mgm.payments.processing.service.entity.redis;

import com.mgm.payments.processing.service.enums.TransactionStatus;
import com.mgm.payments.processing.service.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

import javax.persistence.Column;
import javax.persistence.Id;
import java.math.BigDecimal;

@RedisHash(value = "payment", timeToLive = 1800)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRedisEntity {

    @Id
    @Column(updatable = false,
            nullable = false)
    private String id;
    private TransactionType transactionType;
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
    private String orderType;
    private String cardHolderName;
    private String tenderType;
    private String cardEntryMode;
    private String last4DigitsOfTheCard;
    private String issuerType;
    private String currencyCode;
    private String billingAddress1;
    private String billingAddress2;
    private String billingCity;
    private String billingCountry;
    private String billingState;
    private String billingZipcode;
    private Boolean isCapture;
    private Boolean isRefund;
    private Boolean isVoid;

}
