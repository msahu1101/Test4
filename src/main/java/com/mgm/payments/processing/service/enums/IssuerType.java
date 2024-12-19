package com.mgm.payments.processing.service.enums;

public enum IssuerType {
    VS("Visa"),
    MC("Mastercard"),
    DISCOVER("Discover"),
    DINERSCLUB("Diners Club"),
    JC("JCB"),
    UNIONPAY("Union Pay"),
    GIFTCARD("Gift Card");



    private final String value;

    IssuerType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }


}
