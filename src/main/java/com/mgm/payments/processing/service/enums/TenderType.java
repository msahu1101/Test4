package com.mgm.payments.processing.service.enums;

public enum TenderType {

    CREDITCARD("Credit Card"),
    DEBITCARD("Debit Card"),
    GIFTCARD("Gift Card");
    private final String value;

    TenderType(String value) {

        this.value = value;
    }
    public String getValue() {
        return value;
    }


}
