package com.mgm.payments.processing.service.enums;

public enum SecurityCodeIndicator {

    CSC_0("CVV not provided by user"),
    CSC_1("CVV provided"),
    CSC_2("CVV illegible"),
    CSC_9("CVV not on card, or card did not have a CSC");
    private final String description;

    SecurityCodeIndicator(String description) {

        this.description = description;
    }
    public String getDescription() {
        return description;
    }

}
