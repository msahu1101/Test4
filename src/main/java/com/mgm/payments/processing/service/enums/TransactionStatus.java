package com.mgm.payments.processing.service.enums;

import lombok.Getter;

@Getter
public enum TransactionStatus {

    SUCCESS("001"), FAILURE("002"), PARTIAL("003"), IN_PROCESS("004");
    private final String statusCode;

    TransactionStatus(String statusCode) {
        this.statusCode = statusCode;
    }
}
