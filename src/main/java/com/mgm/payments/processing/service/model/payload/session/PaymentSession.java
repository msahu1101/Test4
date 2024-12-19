package com.mgm.payments.processing.service.model.payload.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentSession {

    private String sessionId;
    private String sessionType;
    private String sessionStatus;
    private String sessionExpiresOn;
    private Transaction transaction;
    private GuestDetails guestDetails;
    private CardDetails cardDetails;
    private OrderItems orderItems;
    private List<KeyValueAttributes> additionalAttributes;


}
