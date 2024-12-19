package com.mgm.payments.processing.service.enums;

import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import lombok.Getter;

@Getter
public enum RouterResponseCode {


    A(PaymentProcessingConstants.ROUTER_RESPONSE_SUCCESS),
    C(PaymentProcessingConstants.ROUTER_RESPONSE_SUCCESS),
    D(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE),
    E(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE),
    F(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE),
    P(PaymentProcessingConstants.ROUTER_RESPONSE_PARTIAL_SUCCESS),
    R(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE),
    X(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE),
    S(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE),
    I(PaymentProcessingConstants.ROUTER_RESPONSE_FAILURE);

    private final String status;

    private RouterResponseCode(String status) {
        this.status = status;
    }

}
