package com.mgm.payments.processing.service.enums;

import lombok.Getter;

@Getter
public enum ApiErrorCode {
    FIELD_VALIDATION_FAILED_MESSAGE("00041-0008-1-00010",
            "One or more Field Validation Failed ::"),

    DUPLICATE_AUTH_REQUEST_CODE("00041-0008-1-00020",
            "Duplicate Request !! Data already present with the same details !!"),

    AUTHORIZE_NOT_EXIST_MESSAGE("00041-0008-1-00030",
            "Capture cannot happen without authorize !!Authorize do not exist for this capture !!"),

    ORDER_NOT_EXIST_MESSAGE("00041-0008-1-00040",
            "Invalid Client Reference number !! Order does not exist for this refund!!"),

    REFUND_LIMIT_EXCEEDS("00041-0008-1-00050",
            "Refund amount cannot be greater than Captured amount  !!"),

    INVALID_VOID_MESSAGE("00041-0008-1-00060",
            "No valid authorize records available to invoke Void !!"),

    VOID_ALREADY_CAPTURED("00041-0008-1-00070",
            "This PaymentId was already captured Successfully and cannot be Void!!"),

    DUPLICATE_CAPTURE_MESSAGE("00041-0008-1-00080",
            "Looks like Capture was already made for this payment id !!"),
    CAPTURE_ALREADY_VOID("00041-0008-1-00090",
            "This PaymentId has been voided already, and it cannot be Captured!!"),
    CAPTURE_AUTH_AMOUNT_MISMATCH("00041-0008-1-00100",
            "The Capture total Amount must be equal to the total Authorized Amount !!"),

    DUPLICATE_VOID_MESSAGE("00041-0008-1-00110",
            "Transaction is already void!! Duplicate Void Call !!"),

    EXPIRED_CARD("00041-0008-1-00120",
            "Card is Expired !! Use valid card !!"),

    PAYMENT_ROUTER_EXCEPTION("00041-0008-0-00210",
            "Exception from Payment Router "),

    PAYMENT_ROUTER_RESPONSE_UPDATE_EXCEPTION("00041-0008-0-00211",
            "Exception while updating the gateway Response in DB"),
    PAYMENT_ROUTER_RESPONSE_PROCESSING_EXCEPTION("00041-0008-0-00212",
            "Exception while updating the gateway Response in PPS response"),
    BAD_REQUEST_EXCEPTION("00041-0008-0-00240", "Bad Request"),
    INTERNAL_SERVER_ERROR("00041-0008-0-00250", "Internal Server Error"),
    RETRY_EXCEEDS_ERROR("00041-0008-0-00260", "Maximum retries reached !!! "),
    PAM_CVS_EXCEPTION("00041-0008-1-00270", "PAM CVS Call failed !!"),
    DUPLICATE_REFUND_MESSAGE("00041-0008-1-00280", "Refund Request already in progress!! Duplicate Refund Call !!"),
    INVALID_DATA_PASSED("00041-0008-1-00290", "JSON Parse error: Invalid Data-type is provided !!" ),
    CLIENT_CONFIG_CALL_ERROR("00041-0008-1-00300", "Exception Occurred while Client Configuration Service Call !!" ),
    INVALID_REFUND_ERROR("00041-0008-1-00310", "Invalid Transaction to process the Refund !!" ),
    SUCCESS_AUTH_DB_ENTRY_EXCEPTION("00041-0008-1-00320", "Void initiated because of DB Exception while saving the Authorize Entry !!"),
    FAILURE_AUTH_DB_ENTRY_EXCEPTION("00041-0008-1-00330", "DB Exception while saving the Authorize Entry !!"),
    FAILURE_CAPTURE_DB_ENTRY_EXCEPTION("00041-0008-1-00340", "DB Exception while saving the Failed Capture Entry !!"),
    PAYMENT_SESSION_EXCEPTION("00041-0008-0-00350",
            "Exception from Payment Session ");

    private final String code;
    private final String description;

    private ApiErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
