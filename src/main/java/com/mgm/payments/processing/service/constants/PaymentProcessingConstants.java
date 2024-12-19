package com.mgm.payments.processing.service.constants;

public class PaymentProcessingConstants {


    public static final String AUTHORIZED_AMOUNT = "authorizedAmount";
    public static final String TOTAL_AMOUNT = "total";
    public static final String FAILURE = "Failure";
    public static final String PPS_AUDIT_SUBJECT = "PPS Audit";
    public static final String CANCELED_BY_MERCHANT = "CANCELED_BY_MERCHANT";
    public static final String COMPLETED = "COMPLETED";
    public static final String PAM_SERVICE = "pamService";
    public static final String PAM_URL = "/api/v1/confirm";
    public static final int MAX_ADDRESS_LENGTH = 70;
    public static final String PPS_AUTH_DB_ENTRY_EXCEPTION = "PPS_AUTH_DB_ENTRY_EXCEPTION";
    public static final String PAYMENT_PROCESSING_REPOSITORY_WRAPPER = "PaymentProcessingRepositoryWrapper";
    public static final String INVOKE_CAPTURE_CONFIRM = "invokeCaptureConfirm";
    public static final String GET_SERVICE_ACCESS_TOKEN = "Get Service Access Token";
    public static final String SERVICE_TOKEN_CALLER = "ServiceTokenCaller";
    public static final String AUTHORIZE_REDIS_PAYMENT_REPOSITORY_WRAPPER = "AuthorizeRedisPaymentRepositoryWrapper";
    public static final String CAPTURE_REDIS_PAYMENT_REPOSITORY_WRAPPER = "CaptureRedisPaymentRepositoryWrapper";
    public static final String REFUND_REDIS_PAYMENT_REPOSITORY_WRAPPER = "RefundRedisPaymentRepositoryWrapper";
    public static final String VOID_REDIS_PAYMENT_REPOSITORY_WRAPPER = "VoidRedisPaymentRepositoryWrapper";
    public static final String REDIS_PAYMENT_REPOSITORY_WRAPPER = "RedisPaymentRepositoryWrapper";
    public static final String PAYMENT_ID_NOT_FOUND = "PaymentId not found in PPS DB!";

    PaymentProcessingConstants() {
    }

    public static final String PPS_API_CONTEXT_PATH = "/api/v1";

    public static final String CORRELATION_ID = "x-mgm-correlation-id";
    public static final String JOURNEY_ID = "x-mgm-journey-id";
    public static final String TRANSACTION_ID = "x-mgm-transaction-id";


    public static final Integer SUCCESS_RESPONSE_STATUS_CODE = 200;

    public static final String SUCCESS_RESPONSE_STATUS = "Success";

    public static final String MGM_SOURCE = "x-mgm-source";
    public static final String CHANNEL = "x-mgm-channel";
    public static final String CLIENT_ID = "x-mgm-client-id";
    public static final String USER_AGENT = "userAgent";

    public static final String ROUTER_URL = "/api/v1/paymentRoutes";

    public static final int MAX_RETRY = 3;

    public static final int DELAY_SECONDS = 5;

    public static final String SERVICE_TOKEN_CLIENT_ID = "client_id";
    public static final String SERVICE_TOKEN_CLIENT_SECRET = "client_secret";
    public static final String SERVICE_TOKEN_GRANT_TYPE = "grant_type";
    public static final String ROUTER_RESPONSE_SUCCESS = "SUCCESS";
    public static final String ROUTER_RESPONSE_PARTIAL_SUCCESS = "PARTIAL";
    public static final String ROUTER_RESPONSE_FAILURE = "FAILURE";
    public static final String WHILE_ACCESS_TOKEN_CALL = " while access token call!!";

    public static final String BEARER = "Bearer ";

    //Logs Formatting Constants
    public static final String PPS_REQUEST_LOG_FORMAT = "{} || {} || REQUEST || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Request: {}";
    public static final String PPS_REQUEST_INFO_LOG_FORMAT = "{} || {} || REQUEST || INFO || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Message: {}";
    public static final String PPS_RESPONSE_LOG_FORMAT = "{} || {} || RESPONSE || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Status: {} || Result: {} || Time: {} ms || Response: {}";
    public static final String PPS_EXCEPTION_LOG_FORMAT = "{} || {} || RESPONSE - EXCEPTION || {} || {} || Source: {} || Origin: {} ||" +
            " journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Status: {} || Result: {} || Time: {} ms || errorResponse: {}";
    public static final String PPS_DB_EXCEPTION_LOG_FORMAT = "{} || {} || RESPONSE - EXCEPTION || DB || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Status: {} || Result: {} || Time: {} ms || Response: {}";
    public static final String PPS_DB_LOG_FORMAT = "{} || {} || RESPONSE || DB || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Time: {} ms || Response: {}";
    public static final String PPS_REQUEST_URL_LOG_FORMAT = "{} || {} || REQUEST || URL || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| External Service Url: {}";
    public static final String PPS_REDIS_CACHE_LOG_FORMAT = "{} || {} || RESPONSE || REDIS || {} || {} || Source: {} || Origin: {} || " +
            "journeyId: {} || correlationId: {} || transactionId: {} || clientId: {} || mgmId: {} || spanId: {} || traceId: {} " +
            "|| Time: {} ms || Response: {}";

    public static final String SERVICE_NAME = "payment-processing-service";
    public static final String AUTHORIZE_OPERATION = "Authorize Call";
    public static final String CAPTURE_OPERATION = "Capture Call";
    public static final String CAPTURE_CONFIRM_OPERATION = "Capture Confirm Call";
    public static final String REFUND_OPERATION = "Refund Call";
    public static final String VOID_OPERATION = "Void Call";
    public static final String CONTROLLER_CLASS_NAME = "PaymentProcessingController";
    public static final String AUTHORIZE_CLASS_NAME = "AuthorizePaymentProcessor";
    public static final String CAPTURE_CLASS_NAME = "CapturePaymentProcessor";
    public static final String CAPTURE_CONFIRM_CLASS_NAME = "CaptureConfirmEventListener";
    public static final String REFUND_CLASS_NAME = "RefundPaymentProcessor";
    public static final String VOID_CLASS_NAME = "VoidPaymentProcessor";
    public static final String EXTERNAL_CLASS_NAME = "PaymentRouterServiceCaller";
    public static final String PAM_CLASS_NAME = "PaymentAuthManagerCaller";
    public static final String SESSION_CLASS_NAME = "SessionServiceCaller";
    public static final String ADVICE_CLASS_NAME = "PaymentProcessingExceptionAdvice";
    public static final String CLIENT_CONFIG_CLASS_NAME = "ClientConfigurationServiceCaller";
    public static final String GET_CLIENT_CONFIG = "Get Client Config";
    public static final String SESSION_RETRIEVE_CALL = "retrieveSession";
    public static final String SESSION_ENDPOINT = "/api/v1/paymentSessions/";

    public static final String PPS_CAPTURE_DB_ENTRY_EXCEPTION = "PPS_CAPTURE_DB_ENTRY_EXCEPTION";

    public static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final int UNIQUE_ID_LENGTH = 18;
    public static final String MGM_CLUSTER_NAME = "x-mgm-cluster";
    public static final String ENV_CLUSTER_NAME = "ClusterName";

}
