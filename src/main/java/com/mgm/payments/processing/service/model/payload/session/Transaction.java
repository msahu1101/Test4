package com.mgm.payments.processing.service.model.payload.session;

import com.mgm.payments.processing.service.enums.CartType;
import com.mgm.payments.processing.service.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transaction{
    private String transactionId;
    private String responseCode;
    private String avsResult;
    private String inauthTransactionId;
    private String parentTransactionId;
    private String checkoutTime;
    private String orderType;
    private CartType cartType;
    private String transactionType;
    private String salesChannel;
    private OrderStatus orderStatus;
    private String processorResponseText;
    private String sessionType;
    private int timeToLive;
    private String sessionId;
}
