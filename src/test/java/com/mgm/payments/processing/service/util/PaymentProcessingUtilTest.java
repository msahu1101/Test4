package com.mgm.payments.processing.service.util;

import com.mgm.payments.processing.service.MockRequestCreator;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfig;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfigPayload;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PaymentProcessingUtil.class)
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
class PaymentProcessingUtilTest {

    MockRequestCreator mockRequestCreator = new MockRequestCreator();
    private User user;
    private HeadersDTO headersDTO;

    @BeforeEach
    void init() {
        user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("web", "1234", "12345", "123456", "WEB", "jwtToken", "clientId", "userAgent");
    }


    @Test
    void getAmountTest() {
        List<Amount> amount = getAmount();
        BigDecimal actualRes = PaymentProcessingUtil.getAmount(amount);
        assertEquals(new BigDecimal(121.0), actualRes);
    }

    @Test
    void testThrowException() {
        String errorCode = "test101";
        String errorMsg = "testMessage";
        assertThrows(PaymentProcessingException.class,
                () -> PaymentProcessingUtil.throwException(errorCode, errorMsg, HttpStatus.PRECONDITION_FAILED));
    }


    @Test
    void getLast4DigitTest() {
        String maskedCardNumber = "************1234";
        String last4Digit = PaymentProcessingUtil.getLast4Digit(maskedCardNumber);
        assertEquals("1234", last4Digit);
    }

    @Test
    void validateCardExpiryTest() {
        assertThrows(PaymentProcessingException.class,
                () -> PaymentProcessingUtil.validateCardExpiry(22, 2010));
    }

    @Test
    void validateCardExpiryTest2() {
        assertThrows(PaymentProcessingException.class,
                () -> PaymentProcessingUtil.validateCardExpiry(22, 20));
    }

    @Test
    void testGetErrorResponse() {
        String error = "{\n" +
                "    \"dateTime\": \"2024-05-06T06:50:27.0332433Z\",\n" +
                "    \"errorCode\": \"00041-0009-0-00510\",\n" +
                "    \"errorMessage\": \"No Client Configurations available for clientId: 12345637891\",\n" +
                "    \"developerMessage\": \"Exception from Payment Router AUTHORIZE!! 400 BAD_REQUEST\",\n" +
                " 	 \"originError\": \"No Client configurations available for clientId: 12345637891\"\n" +
                "}";
        PaymentExceptionResponse errorResponse = PaymentProcessingUtil.getErrorResponse(error, "1001001002", " developer message");
        assertEquals("00041-0009-0-00510", errorResponse.getErrorCode());
    }

    @Test
    void testGetErrorResponseException() {
        String error = "{\n" +
                "    \"dateTime\": \"2024-05-06T06:50:27.0332433Z\",\n" +
                "    \"errorCode\": \"00041-0009-0-00510\"\n" +
                " 	 \"originError\": \"No Client configurations available for clientId: 12345637891\"\n" +
                "}";
        PaymentExceptionResponse errorResponse = PaymentProcessingUtil.getErrorResponse(error, "1001001002", " developer message");
        assertEquals("00041-0008-0-00210", errorResponse.getErrorCode());
    }

    @Test
    void getCardEntryModeTest1() {
        PaymentRequest req = PaymentRequest.builder().payment(Payment.builder().cardPresent(CardPresent.Y).build()).build();
        String actualRes = PaymentProcessingUtil.getCardEntryMode(req);
        assertEquals("CP", actualRes);
    }

    @Test
    void getCardEntryModeTest2() {
        PaymentRequest req = PaymentRequest.builder().payment(Payment.builder().cardPresent(CardPresent.N).build()).build();
        String actualRes = PaymentProcessingUtil.getCardEntryMode(req);
        assertEquals("CNP", actualRes);
    }

    @Test
    void testBuildAuditData() throws IOException {
        String eventName = "PPS_REFUND";
        String eventDesc = "Refund description";
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        PaymentEntity paymentEntity = getPaymentList().get(0);
        paymentEntity.setGatewayId("SHFT");
        paymentEntity.setMgmToken("1010101010234");
        paymentEntity.setLast4DigitsOfTheCard("0234");

        paymentRequest.getPayment().getTenderDetails().setMgmToken(paymentEntity.getMgmToken());
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        AuditData auditData = PaymentProcessingUtil.buildAuditData(eventName, eventDesc, null, "", paymentRequest, prResponse, paymentEntity);
        String actualRes = auditData.getEventName();
        assertEquals(eventName, actualRes);
    }

    @Test
    void testIsAdhocRefundAllowed() {
        ClientConfigPayload clientConfigPayload = ClientConfigPayload.builder().clientId("1234567890")
                .configDetails(getClientConfig(true)).build();
        boolean actualRes = PaymentProcessingUtil.isAdhocRefundAllowed(clientConfigPayload, "PaymentConfigs", "adhocRefund");
        assertTrue(actualRes);
    }

    @Test
    void testIsAdhocRefundAllowedFalse() {
        ClientConfigPayload clientConfigPayload = ClientConfigPayload.builder().clientId("1234567890")
                .configDetails(getClientConfig(false)).build();
        boolean actualRes = PaymentProcessingUtil.isAdhocRefundAllowed(clientConfigPayload, "PaymentConfigs", "adhocRefund");
        assertFalse(actualRes);
    }

    @Test
    void testIsAdhocRefundAllowedNoClient() {
        boolean actualRes = PaymentProcessingUtil.isAdhocRefundAllowed(null, "PaymentConfigs", "adhocRefund");
        assertTrue(actualRes);
    }

    private List<ClientConfig> getClientConfig(Boolean flag) {
        List<ClientConfig> clientConfigs = new ArrayList<>();
        Map<String, Boolean> configMap = new HashMap<>();
        configMap.put("adhocRefund", flag);
        clientConfigs.add(ClientConfig.builder().configName("PaymentConfigs").configValue(configMap).build());

        return clientConfigs;
    }

    private List<PaymentEntity> getPaymentList() {
        List<PaymentEntity> paymentList = new ArrayList<>();

        paymentList.add(PaymentEntity.builder().paymentId(UUID.randomUUID().toString()).gatewayChainId("12345")
                .sessionId("session123").clientReferenceNumber("101").groupId("g1").amount(new BigDecimal(121.0)).authorizedAmount(new BigDecimal(120.0)).authChainId(1)
                .clientId(headersDTO.getClientId()).orderType(OrderType.ROOM.toString()).mgmId(user.getMgmId())
                .cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue()).issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD)
                .transactionType(TransactionType.AUTHORIZE).transactionStatus(TransactionStatus.SUCCESS).build());

        paymentList.add(PaymentEntity.builder().paymentId(UUID.randomUUID().toString()).gatewayChainId("12345")
                .clientReferenceNumber("101").groupId("1").amount(new BigDecimal("121.0")).authorizedAmount(new BigDecimal("120.0")).authChainId(2)
                .clientId(headersDTO.getClientId()).orderType(OrderType.ROOM.toString()).mgmId(user.getMgmId())
                .cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue()).issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD)
                .transactionType(TransactionType.CAPTURE).transactionStatus(TransactionStatus.SUCCESS).build());


        paymentList.add(PaymentEntity.builder().paymentId(UUID.randomUUID().toString()).gatewayChainId("12345")
                .clientReferenceNumber("101").groupId("g1").amount(new BigDecimal("200.0")).authChainId(1).clientId(headersDTO.getClientId())
                .orderType(OrderType.ROOM.toString()).mgmId(user.getMgmId()).cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue())
                .issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD).transactionType(TransactionType.REFUND)
                .transactionStatus(TransactionStatus.SUCCESS).build());
        return paymentList;
    }

    private List<Amount> getAmount() {
        List<Amount> amountList = new ArrayList<>();
        amountList.add(Amount.builder().name("total").value(new BigDecimal(121.0)).build());
        amountList.add(Amount.builder().name("tax").value(new BigDecimal(3.0)).build());
        amountList.add(Amount.builder().name("authorized_amount").value(new BigDecimal(119.0)).build());

        return amountList;
    }

}
