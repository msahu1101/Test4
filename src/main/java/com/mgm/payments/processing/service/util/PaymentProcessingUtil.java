package com.mgm.payments.processing.service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.entity.redis.PaymentRedisEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.PaymentEntityDTO;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfig;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfigPayload;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import com.mgm.payments.processing.service.model.payload.session.Item;
import com.mgm.payments.processing.service.model.payload.session.ItemAuthGroup;
import com.mgm.payments.processing.service.model.payload.session.KeyValueAttributes;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONValue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.validation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;

@Slf4j
@Component
public class PaymentProcessingUtil {

    PaymentProcessingUtil() {
    }

    private static final Random random = new Random();


    public static BigDecimal getAmount(List<Amount> amount) {
        if(amount != null && !amount.isEmpty()) {
            List<Amount> amountList = amount.stream().filter(amt -> amt.getName().equals("total"))
                    .collect(Collectors.toList());
            return amountList.get(0).getValue();
        }
        return new BigDecimal(0);
    }



    public static void throwException(String errorCode, String errorMessage, HttpStatus preconditionFailed) {
        PaymentExceptionResponse exception = new PaymentExceptionResponse();
        exception.setDateTime(ZonedDateTime.now());
        exception.setErrorCode(errorCode);
        exception.setErrorMessage(errorMessage);
        throw new PaymentProcessingException(exception, preconditionFailed);
    }


    public static String getLast4Digit(String maskedCardNumber) {

        return maskedCardNumber != null && maskedCardNumber.length() >= 4 ? maskedCardNumber.substring(maskedCardNumber.length() - 4) : null;

    }

    public static void validateCardExpiry(Integer expiryMonth, Integer expiryYear) {

        int currentMonth = ZonedDateTime.now().getMonthValue();
        int currentYear = ZonedDateTime.now().getYear();
        if (expiryYear < 100) {
            currentYear = currentYear % 100;
        }
        if ((expiryYear > currentYear + 20) || (expiryYear < currentYear) || (expiryYear == currentYear && expiryMonth < currentMonth)) {

            PaymentProcessingUtil.throwException(
                    ApiErrorCode.EXPIRED_CARD.getCode(),
                    ApiErrorCode.EXPIRED_CARD.getDescription(), HttpStatus.PRECONDITION_FAILED);
        }
    }

    public static PaymentExceptionResponse getErrorResponse(String error, String paymentId, String developerMessage) {
        LinkedHashMap<String, Object> routerException = new LinkedHashMap<>();
        String originError = null;
        String originKey = "originError";
        String errorMsg = ApiErrorCode.PAYMENT_ROUTER_EXCEPTION.getDescription();
        String errorCode = ApiErrorCode.PAYMENT_ROUTER_EXCEPTION.getCode();
        String errorMessage = null;
        try {
            routerException = new ObjectMapper().readValue(error, LinkedHashMap.class);
            if (routerException != null) {
                errorMessage = routerException.get("errorMessage") != null ?
                        routerException.get("errorMessage").toString() : null;
                errorCode = routerException.get("errorCode") != null ?
                        routerException.get("errorCode").toString()
                        : errorCode;
                if (routerException.get(originKey) != null) {
                    if (routerException.get(originKey) instanceof Map) {
                        originError = JSONValue.toJSONString(routerException.get(originKey));
                    } else {
                        originError = routerException.get(originKey).toString();
                    }
                }
            }

        } catch (JsonProcessingException e) {
            originError = error;
        }
        if (errorMessage == null) {
            assert routerException != null;
            errorMessage = routerException.get("error") != null ? routerException.get("error").toString() : errorMsg;
            originError = error;
        }
        return PaymentExceptionResponse.builder()
                .dateTime(ZonedDateTime.now())
                .paymentId(paymentId)
                .developerMessage(developerMessage)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .originError(originError)
                .dateTime(ZonedDateTime.now())
                .build();

    }

    public static String getCardEntryMode(PaymentRequest request){
        return CardPresent.N.equals(request.getPayment().getCardPresent()) ? "CNP" : "CP";
    }


    public static AuditData buildAuditData(String eventName, String eventDescription, String mgmErrorCode, String errorDescription,
                                           Object requestPayload, Object paymentResponse, PaymentEntity paymentEntity) {
        String gatewayId = "";
        String lastFour = "";
        String mgmToken = "";
        String processorToken = "";
        if (paymentEntity != null) {
            gatewayId = paymentEntity.getGatewayId();
            lastFour = paymentEntity.getLast4DigitsOfTheCard();
            mgmToken = paymentEntity.getMgmToken();
        }

        if(paymentResponse instanceof PaymentRouterResponse){
            PaymentRouterResponse prResponse = (PaymentRouterResponse) paymentResponse;
            processorToken = prResponse.getResults()!=null && !prResponse.getResults().isEmpty() && prResponse.getResults().get(0).getGatewayResult() != null &&
                    prResponse.getResults().get(0).getGatewayResult().getCard() != null ? prResponse.getResults().get(0).getGatewayResult().getCard().getGatewayToken(): "";
        }
        return AuditData.builder()
                .eventName(eventName)
                .eventDescription(eventDescription)
                .startTimeTS(LocalDateTime.now())
                .endTimeTS(LocalDateTime.now())
                .serviceName(AuditTrailConstants.SERVICE_NAME)
                .mgmErrorCode(mgmErrorCode)
                .externalErrorCode("")
                .errorDescription(errorDescription)
                .subject(PaymentProcessingConstants.PPS_AUDIT_SUBJECT)
                .requestPayload(convertStringToObject(requestPayload))
                .responsePayload(convertStringToObject(paymentResponse))
                .gateWayId(gatewayId)
                .lastFour(lastFour)
                .mgmToken(mgmToken)
                .processorToken(processorToken)
                .build();


    }

    private static Object convertStringToObject(Object req) {
        if (req instanceof String && req != "") {
            ObjectMapper om = new ObjectMapper();
            String maskRequest = (String) req;
            if (maskRequest.contains("{")) {
                try {
                    return om.readValue(maskRequest, Object.class);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    return req;
                }
            } else {
                return req;
            }
        } else {
            return req;
        }
    }

    public static boolean isAdhocRefundAllowed(ClientConfigPayload clientConfig, String configName, String configValue) {
        if(clientConfig == null){
            return true;
        }
        List<ClientConfig> clientConfigs = clientConfig.getConfigDetails();
        Optional<ClientConfig> clientConfigOptional = clientConfigs.stream().filter(config ->
                config.getConfigName().equals(configName)).findAny();
        if(clientConfigOptional.isPresent()){
            Object configValueObj = clientConfigOptional.get().getConfigValue();
            if(configValueObj instanceof HashMap){
                Map<String, Boolean> configMap = (Map<String, Boolean>) configValueObj;
                return configMap.getOrDefault(configValue, false);
            }
        }
        return false;
    }

    public static void mapDerivedClientId(PaymentSession paymentSession, HeadersDTO headersDTO, PaymentRequest paymentRequest) {
        String itemId = paymentRequest.getItemId();
        if (paymentSession.getTransaction() != null && paymentSession.getTransaction().getTransactionType() != null) {

            paymentSession.getOrderItems().getItemAuthGroups().forEach(itemAuthGroup -> {
                Item item = itemAuthGroup.getItems().stream()
                        .filter(i -> Objects.equals(i.getItemId(), itemId))
                        .findFirst()
                        .orElse(null);

                if (item != null && item.getItemType() != null && paymentSession.getTransaction().getTransactionType() != null) {
                    String clientId = "MGM|" + item.getItemType().toUpperCase() + "|" +
                            paymentSession.getTransaction().getTransactionType().toUpperCase() + "|" +
                            item.getPropertyId();
                    if (item.getItemType().equals("Show")) {
                        clientId += "||" + item.getSeasonId();
                    }
                    headersDTO.setClientId(clientId);
                }
                if(item != null && item.getItemType() != null && item.getItemId().equalsIgnoreCase(paymentRequest.getItemId()) && item.getItemType().equalsIgnoreCase("Room")){
                    paymentRequest.setHotelData(getHotelData(itemAuthGroup, item));
                }
            });
        }
    }

    public static HotelData getHotelData(ItemAuthGroup itemAuthGroup, Item item) {
        HotelData hotelData = new HotelData();
        hotelData.setCheckInDate(itemAuthGroup.getItems().get(0).getDuration().getStartDate());
        hotelData.setCheckOutDate(itemAuthGroup.getItems().get(0).getDuration().getEndDate());
        List<KeyValueAttributes> totalAmount = item.getAmount().getTotalAmount();
        totalAmount.forEach(attribute -> {
            if ("total".equalsIgnoreCase(attribute.getName())) {
                hotelData.setRoomRate(attribute.getValue());
            }
        });
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate checkInDate = LocalDate.parse(hotelData.getCheckInDate(), formatter);
        LocalDate checkOutDate = LocalDate.parse(hotelData.getCheckOutDate(), formatter);
        long durationDays = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        hotelData.setExpectedDuration(String.valueOf(durationDays > 0 ? durationDays : 1));
        return hotelData;
    }

    public static String sanitize(String input) {
        if (input!=null) {
            return input.replace("\r", "").replace("\n", "");
        }
        return null;
    }

    public static void publishRequestLog(String format, String operationClassName, String headersParams, String spanTrace, String statusResult, Long time, String maskedPayload){
        String[] headers = headersParams.split(",");
        String mgmSource = PaymentProcessingUtil.sanitize(headers[0]);
        String mgmChannel = PaymentProcessingUtil.sanitize(headers[1]);
        String mgmJourneyId = PaymentProcessingUtil.sanitize(headers[2]);
        String mgmCorrelationId = PaymentProcessingUtil.sanitize(headers[3]);
        String mgmTransactionId = PaymentProcessingUtil.sanitize(headers[4]);
        String mgmClientId = PaymentProcessingUtil.sanitize(headers[5]);
        String mgmId = PaymentProcessingUtil.sanitize(headers[6]);
        String[] spanTraces = spanTrace.split(",");
        String spanId = spanTraces[0];
        String traceId = spanTraces[1];
        String[] operationClassNames = operationClassName.split(",");
        String operation = operationClassNames[0];
        String className = operationClassNames[1];
        if (statusResult == null) {
            log.info(format, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, operation, className, mgmSource, mgmChannel, mgmJourneyId, mgmCorrelationId,
                   mgmTransactionId, mgmClientId, mgmId, spanId, traceId, PaymentProcessingUtil.sanitize(maskedPayload));
            return;
        }
        String[] statusResults = statusResult.split(",");
        String status = statusResults[0];
        String result = statusResults[1];
        log.info(format, ZonedDateTime.now(ZoneId.of("UTC")), SERVICE_NAME, operation, className, mgmSource, mgmChannel, mgmJourneyId, mgmCorrelationId,
                mgmTransactionId, mgmClientId, mgmId, spanId, traceId, status, result,
                time, PaymentProcessingUtil.sanitize(maskedPayload));
    }

    public static String concatenateWithComma(String... strings) {
        return String.join(",", strings);
    }

    public static List<PaymentEntity> mapToEntity(List<PaymentEntityDTO> paymentEntityDTOList) {
        if(paymentEntityDTOList == null || paymentEntityDTOList.isEmpty()){
            return new ArrayList<>();
        }
        return paymentEntityDTOList.stream().map(dto -> {
            PaymentEntity entity = new PaymentEntity();
            entity.setPaymentId(dto.getPaymentId());
            entity.setTransactionType(dto.getTransactionType());
            entity.setTransactionStatus(dto.getTransactionStatus());
            entity.setAmount(dto.getAmount());
            entity.setAuthorizedAmount(dto.getAuthorizedAmount());
            entity.setMgmToken(dto.getMgmToken());
            entity.setClientReferenceNumber(dto.getClientReferenceNumber());
            entity.setReferenceId(dto.getReferenceId());
            entity.setSessionId(dto.getSessionId());
            entity.setMgmId(dto.getMgmId());
            entity.setGatewayChainId(dto.getGatewayChainId());
            entity.setGroupId(dto.getGroupId());
            entity.setGatewayId(dto.getGatewayId());
            entity.setClientId((dto.getClientId()));
            entity.setOrderType(dto.getOrderType());
            entity.setCardHolderName(dto.getCardHolderName());
            entity.setTenderType(dto.getTenderType());
            entity.setCardEntryMode(dto.getCardEntryMode());
            entity.setLast4DigitsOfTheCard(dto.getLast4DigitsOfTheCard());
            entity.setIssuerType(dto.getIssuerType());
            entity.setCurrencyCode(dto.getCurrencyCode());
            entity.setBillingAddress1(dto.getBillingAddress1());
            entity.setBillingAddress2(dto.getBillingAddress2());
            entity.setBillingCity(dto.getBillingCity());
            entity.setBillingCountry(dto.getBillingCountry());
            entity.setBillingState(dto.getBillingState());
            entity.setBillingZipcode(dto.getBillingZipcode());
            entity.setCreatedTimestamp(dto.getCreatedTimestamp());
            entity.setRequestChannel(dto.getRequestChannel());
            return entity;
        }).collect(Collectors.toList());
    }

    public static PaymentRedisEntity mapToRedisEntity(PaymentEntity payment) {
        // map all the fields of PaymentRedisEntity from PaymentEntity
        return PaymentRedisEntity.builder()
                .id(payment.getPaymentId())
                .transactionType(TransactionType.AUTHORIZE)
                .transactionStatus(payment.getTransactionStatus())
                .amount(payment.getAmount())
                .authorizedAmount(payment.getAuthorizedAmount())
                .mgmToken(payment.getMgmToken())
                .clientReferenceNumber(payment.getClientReferenceNumber())
                .referenceId(payment.getReferenceId())
                .sessionId(payment.getSessionId())
                .mgmId(payment.getMgmId())
                .gatewayChainId(payment.getGatewayChainId())
                .groupId(payment.getGroupId())
                .gatewayId(payment.getGatewayId())
                .orderType(payment.getOrderType())
                .cardHolderName(payment.getCardHolderName())
                .tenderType(payment.getTenderType())
                .cardEntryMode(payment.getCardEntryMode())
                .last4DigitsOfTheCard(payment.getLast4DigitsOfTheCard())
                .issuerType(payment.getIssuerType())
                .currencyCode(payment.getCurrencyCode().toString())
                .billingAddress1(payment.getBillingAddress1())
                .billingAddress2(payment.getBillingAddress2())
                .billingCity(payment.getBillingCity())
                .billingCountry(payment.getBillingCountry())
                .billingState(payment.getBillingState())
                .billingZipcode(payment.getBillingZipcode())
                .isCapture(Boolean.FALSE)
                .isRefund(Boolean.FALSE)
                .isVoid(Boolean.FALSE)
                .build();
    }

    public static List<PaymentEntity> mapToEntityList(PaymentRedisEntity paymentRedisEntity) {
        // map all the fields of PaymentEntity from PaymentRedisEntity
        PaymentEntity paymentEntity = new PaymentEntity();
        paymentEntity.setPaymentId(paymentRedisEntity.getId());
        paymentEntity.setTransactionType(TransactionType.AUTHORIZE);
        paymentEntity.setTransactionStatus(paymentRedisEntity.getTransactionStatus());
        paymentEntity.setAmount(paymentRedisEntity.getAmount());
        paymentEntity.setAuthorizedAmount(paymentRedisEntity.getAuthorizedAmount());
        paymentEntity.setMgmToken(paymentRedisEntity.getMgmToken());
        paymentEntity.setClientReferenceNumber(paymentRedisEntity.getClientReferenceNumber());
        paymentEntity.setReferenceId(paymentRedisEntity.getReferenceId());
        paymentEntity.setSessionId(paymentRedisEntity.getSessionId());
        paymentEntity.setMgmId(paymentRedisEntity.getMgmId());
        paymentEntity.setGatewayChainId(paymentRedisEntity.getGatewayChainId());
        paymentEntity.setGroupId(paymentRedisEntity.getGroupId());
        paymentEntity.setGatewayId(paymentRedisEntity.getGatewayId());
        paymentEntity.setOrderType(paymentRedisEntity.getOrderType());
        paymentEntity.setCardHolderName(paymentRedisEntity.getCardHolderName());
        paymentEntity.setTenderType(paymentRedisEntity.getTenderType());
        paymentEntity.setCardEntryMode(paymentRedisEntity.getCardEntryMode());
        paymentEntity.setLast4DigitsOfTheCard(paymentRedisEntity.getLast4DigitsOfTheCard());
        paymentEntity.setIssuerType(paymentRedisEntity.getIssuerType());
        paymentEntity.setCurrencyCode(CurrencyCode.valueOf(paymentRedisEntity.getCurrencyCode()));
        paymentEntity.setBillingAddress1(paymentRedisEntity.getBillingAddress1());
        paymentEntity.setBillingAddress2(paymentRedisEntity.getBillingAddress2());
        paymentEntity.setBillingCity(paymentRedisEntity.getBillingCity());
        paymentEntity.setBillingCountry(paymentRedisEntity.getBillingCountry());
        paymentEntity.setBillingState(paymentRedisEntity.getBillingState());
        paymentEntity.setBillingZipcode(paymentRedisEntity.getBillingZipcode());
        return List.of(paymentEntity);
    }

    public static PaymentEntity getAuthEntity(List<PaymentEntity> paymentList) {
        return paymentList.stream().filter(paymentEntity -> paymentEntity.getTransactionType().equals(TransactionType.AUTHORIZE)).findFirst().orElse(null);
    }

    public static void validateRefundRequest(PaymentRequest paymentRequest) {
        String errorCode = ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getCode();
        String errorMessage = ApiErrorCode.FIELD_VALIDATION_FAILED_MESSAGE.getDescription();

        validateField(paymentRequest.getSessionId(), "paymentRequest.sessionId", errorCode, errorMessage);
        validateField(paymentRequest.getItemId(), "paymentRequest.itemId", errorCode, errorMessage);

        Optional.ofNullable(paymentRequest.getPayment())
                .map(Payment::getTenderDetails)
                .ifPresent(tenderDetails -> {
                    validateField(tenderDetails.getExpireMonth(), "paymentRequest.payment.tenderDetails.expireMonth",
                            errorCode, errorMessage);
                    validateField(tenderDetails.getExpireYear(), "paymentRequest.payment.tenderDetails.expireYear",
                            errorCode, errorMessage);
                    validateTenderDetails(tenderDetails);
                });
    }

    private static void validateField(String field, String fieldName, String errorCode, String errorMessage) {
        if (field == null || field.isBlank()) {
            String message = String.format("%s : Must not be null or blank", fieldName);
            throwException(errorCode, errorMessage.concat(message), HttpStatus.PRECONDITION_FAILED);
        }
    }

    private static void validateTenderDetails(TenderDetails tenderDetails) {
        Payment payment = new Payment();
        payment.setTenderDetails(tenderDetails);

        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validatorInstance = factory.getValidator();
        validatorInstance.validate(payment);

    }

    public static String generateUniqueId(long snowFlakeId) {
        String atomicId = String.valueOf(snowFlakeId);
        int snowFlakeIdLen = atomicId.length();
        int remainingLength = UNIQUE_ID_LENGTH - snowFlakeIdLen;
        StringBuilder randomChars = new StringBuilder(remainingLength);
        for (int i = 0; i < remainingLength; i++) {
            randomChars.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        StringBuilder uniqueId = new StringBuilder(atomicId);
        for (int i = 0; i < remainingLength; i++) {
            int randomPosition = random.nextInt(snowFlakeIdLen + i);
            uniqueId.insert(randomPosition, randomChars.charAt(i));
        }
        return uniqueId.toString();
    }

    public static void getFailureEntity(PaymentEntity payment, Exception e) {
        String gatewayTransactionStatusCode = payment.getGatewayTransactionStatusCode();
        String gatewayTransactionStatusReason = payment.getGatewayTransactionStatusReason();
        String responseCode = payment.getGatewayResponseCode();
        payment.setGatewayResponseCode(responseCode != null ? responseCode : "E");
        if(gatewayTransactionStatusCode == null || gatewayTransactionStatusReason == null) {
            payment.setGatewayTransactionStatusCode("500");
            payment.setGatewayTransactionStatusReason("Router Service Connection Failure");
            if (e instanceof PaymentProcessingException) {
                PaymentProcessingException pe = (PaymentProcessingException) e;
                payment.setGatewayTransactionStatusCode(pe.getHttpStatus() != null ? String.valueOf(pe.getHttpStatus().value()) : "500");
                if (pe.getExceptionResponse() != null && pe.getExceptionResponse().getErrorCode() != null
                        && pe.getExceptionResponse().getErrorCode().equals("00041-0009-0-00180")) {
                    payment.setGatewayTransactionStatusReason("FreedomPay Service Connection Failure");
                }
            }
            payment.setTransactionStatus(TransactionStatus.FAILURE);
        }
    }

    /**
     * Converts the Date time given as string and compares with the current date
     * @param dateTime string date time
     * @return true - if the date part is same as today
     *          false - if the date part is not same as today
     */
    public static boolean compareDate(String dateTime){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate localDate = LocalDate.parse(dateTime.substring(0,10), formatter);
        return localDate.isEqual(LocalDate.now());
    }
}
