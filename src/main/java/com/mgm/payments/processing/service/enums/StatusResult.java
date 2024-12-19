package com.mgm.payments.processing.service.enums;

import lombok.Getter;

@Getter
public enum StatusResult {
    S("Accept"),
    F("Decline"),
    E("Fail");

    private final String result;

    StatusResult(String result) {
        this.result = result;
    }
}
