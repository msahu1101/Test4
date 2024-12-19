create table t_payment
(
    PAYMENT_ID                        varchar(50) not null primary key,
    REFERENCE_ID                      varchar(50),
    GROUP_ID                          varchar(50),
    GATEWAY_RRN                       varchar(50),
    GATEWAY_CHAIN_ID                  varchar(50),
    CLIENT_REFERENCE_NUMBER           varchar(50) ,
    AMOUNT                            DECIMAL(10,2),
    AUTHORIZED_AMOUNT                 DECIMAL(10,2),
    CUMULATIVE_AMOUNT                 DECIMAL(10,2),
    AUTH_CHAIN_ID                     DECIMAL,
    GATEWAY_ID                        char(4),
    CLIENT_ID                         varchar(50),
    ORDER_TYPE                        varchar(50),
    MGM_ID                            varchar(50),
    MGM_TOKEN                         varchar(50),
    CARD_HOLDER_NAME                  varchar(50),
    TENDER_TYPE                       varchar(50),
    TENDER_CATEGORY                   varchar(50),
    ISSUER_TYPE                       varchar(50),
    CURRENCY_CODE                     varchar(50),
    LAST4DIGITS_OF_THE_CARD           varchar(50),
    BILLING_ADDRESS_1                 varchar(70),
    BILLING_ADDRESS_2                 varchar(70),
    BILLING_CITY                      varchar(50),
    BILLING_ZIPCODE                   varchar(10),
    BILLING_STATE                     varchar(20),
    BILLING_COUNTRY                   varchar(20),
    PAYMENT_AUTH_ID                   varchar(50),
    CLERK_ID                          varchar(50),
    TRANSACTION_TYPE                  varchar(10),
    TRANSACTION_STATUS                varchar(50),
    GATEWAY_TRANSACTION_STATUS_CODE   varchar(10),
    GATEWAY_RESPONSE_CODE             varchar(40),
    GATEWAY_TRANSACTION_STATUS_REASON varchar(500),
    PAYMENT_AUTH_SOURCE               varchar(50),
    DEFERRED_AUTH                     varchar(40),
    MGM_CORRELATION_ID                varchar(75),
    MGM_JOURNEY_ID                    varchar(50),
    MGM_TRANSACTION_ID                varchar(50),
    REQUEST_CHANNEL                   varchar(50),
    SESSION_ID                        varchar(50),
    CARD_ENTRY_MODE                   varchar(20),
    AVS_RESPONSE_CODE                 varchar(20),
    CVV_RESPONSE_CODE                 varchar(20),
    DCC_FLAG                          varchar(5),
    DCC_CONTROL_NUMBER                varchar(20),
    DCC_AMOUNT                        varchar(20),
    DCC_BIN_RATE                      varchar(20),
    DCC_BIN_CURRENCY                  varchar(3),
    PROCESSOR_STATUS_CODE             varchar(20),
    PROCESSOR_STATUS_MESSAGE          varchar(50),
    PROCESSOR_AUTH_CODE               varchar(20),
    AUTH_SUBTYPE                      varchar(20),
    CREATED_BY                        varchar(50),
    CREATED_TIMESTAMP                 datetimeoffset,
    UPDATED_BY                        varchar(50),
    UPDATED_TIMESTAMP                 datetimeoffset

)


