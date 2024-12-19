package com.mgm.payments.processing.service.constants;


public class AuditTrailConstants {

    AuditTrailConstants() {
    }

    public static final String SUBJECT = "Audit Trail";
    public static final String EVENT_TYPE = "mgm.payments.audittrail";
    public static final String DATA_VERSION = "0.1";
    public static final String SERVICE_NAME = "Payment Processing Service";
    public static final String TOPIC = "mgmAuditTrailTopic";


    public static final String TOPIC_ENDPOINT = "TopicEndpoint";
    public static final String TOPIC_KEY = "TopicKey";
    public static final String TOPIC_KEY_NAME = "TopicKey1";
    public static final String PPS_AUTHORIZE = "PPS_AUTHORIZE";
    public static final String PPS_CAPTURE = "PPS_CAPTURE";
    public static final String PPS_REFUND = "PPS_REFUND";
    public static final String PPS_VOID = "PPS_VOID";


}
