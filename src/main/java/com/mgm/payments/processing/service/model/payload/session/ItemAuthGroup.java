package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ItemAuthGroup {
    private String groupId;
    private Integer authDeclineCount;
    private Integer authFraudDeclineCount;
    private Integer verifyDeclineCount;
    private Integer verifyFraudDeclineCount;
    private List<KeyValueAttributes> itemsGroupTotal;
    private List<PaymentAuthResult> paymentAuthResults;
    private PaymentFraudResult paymentFraudResults;
    private PaymentVerifyResult paymentVerifyResults;
    private String clientId;
    private List<Item> items;
}

