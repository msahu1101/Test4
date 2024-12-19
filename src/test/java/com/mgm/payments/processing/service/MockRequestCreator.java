package com.mgm.payments.processing.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;


public class MockRequestCreator {


    public PaymentRequest createMockAuthorizeRequest() throws IOException {
        return (PaymentRequest) createRequest("/authorizeRequest.json", PaymentRequest.class);
    }

    public PaymentRequest createMockCaptureRequest() throws IOException {
        return (PaymentRequest) createRequest("/captureRequest.json", PaymentRequest.class);
    }

    public PaymentRequest createMockRefundRequest() throws IOException {
        return (PaymentRequest) createRequest("/refundRequest.json", PaymentRequest.class);
    }

    public PaymentRouterResponse createMockRouterResponse() throws IOException {
        return (PaymentRouterResponse) createRequest("/paymentRouterResponse.json", PaymentRouterResponse.class);
    }

    public PaymentRouterResponse createMockRouterErrorResponse() throws IOException {
        return (PaymentRouterResponse) createRequest("/paymentRouterErrorResponse.json", PaymentRouterResponse.class);
    }

    public PaymentRequest createMockVoidRequest() {
        return PaymentRequest.builder().paymentId("payment-id").clientReferenceNumber("order-ref").build();
    }

    private Object createRequest(String file, Class classType) throws IOException {
        Object result = null;
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (Reader reader = new InputStreamReader(this.getClass().getResourceAsStream(file))) {
            result = objectMapper.readValue(reader, classType);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    public PaymentEntity createAuthPaymentEntity(HeadersDTO headersDTO, User user) {
        return PaymentEntity.builder().paymentId(UUID.randomUUID().toString()).gatewayChainId("12345")
                .clientReferenceNumber("101").groupId("groupId123").amount(new BigDecimal(121.0)).authorizedAmount(new BigDecimal(120.0)).authChainId(1)
                .clientId(headersDTO.getClientId()).orderType(OrderType.ROOM.toString())
                .mgmId(user.getMgmId()).cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue()).issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD)
                .billingAddress1("22").billingAddress2("Street- MT-BL").billingCity("MTH").billingCountry("IND")
                .billingState("BR").billingZipcode("845401").transactionType(TransactionType.AUTHORIZE).gatewayId("SHFT")
                .transactionStatus(TransactionStatus.SUCCESS).createdTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString())
                .createdBy(user.getMgmId()).mgmCorrelationId("correlation123").mgmJourneyId("journey123")
                .sessionId("session123").last4DigitsOfTheCard("1234").build();
    }

    public PaymentEntity createMockCapturePaymentEntity(HeadersDTO headersDTO, User user) {
        return PaymentEntity.builder().paymentId(UUID.randomUUID().toString()).gatewayChainId("12345")
                .clientReferenceNumber("101").amount(new BigDecimal(121.0)).authorizedAmount(new BigDecimal(120.0)).authChainId(2)
                .clientId(headersDTO.getClientId()).orderType(OrderType.ROOM.toString())
                .mgmId(user.getMgmId()).cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue()).issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD)
                .billingAddress1("22").billingAddress2("Street- MT-BL").billingCity("MTH").billingCountry("IND")
                .billingState("BR").billingZipcode("845401").transactionType(TransactionType.CAPTURE)
                .transactionStatus(TransactionStatus.SUCCESS).createdTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString())
                .createdBy(user.getMgmId()).mgmCorrelationId("correlation123").mgmJourneyId("journey123")
                .sessionId("session123").build();
    }

    public PaymentEntity createMockRefundEntity(HeadersDTO headersDTO, User user) {
        return PaymentEntity.builder()
                .clientReferenceNumber("101").amount(new BigDecimal(121.0)).authorizedAmount(new BigDecimal(120.0)).authChainId(3)
                .clientId(headersDTO.getClientId()).orderType(OrderType.ROOM.toString())
                .mgmId(user.getMgmId()).cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue()).issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD)
                .billingAddress1("22").billingAddress2("Street- MT-BL").billingCity("MTH").billingCountry("IND")
                .billingState("BR").billingZipcode("845401").transactionType(TransactionType.REFUND)
                .transactionStatus(TransactionStatus.SUCCESS).createdTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString())
                .createdBy(user.getMgmId()).mgmCorrelationId("correlation123").mgmJourneyId("journey123")
                .sessionId("session123").build();
    }

    public PaymentEntity createMockVoidEntity(HeadersDTO headersDTO, User user) {
        return PaymentEntity.builder().paymentId(UUID.randomUUID().toString()).gatewayChainId("12345")
                .clientReferenceNumber("101").amount(new BigDecimal(121.0)).authorizedAmount(new BigDecimal(120.0)).authChainId(3)
                .clientId(headersDTO.getClientId()).orderType(OrderType.ROOM.toString())
                .mgmId(user.getMgmId()).cardHolderName(user.getFirstName())
                .tenderType(TenderType.CREDITCARD.getValue()).issuerType(IssuerType.VS.getValue()).currencyCode(CurrencyCode.USD)
                .billingAddress1("22").billingAddress2("Street- MT-BL").billingCity("MTH").billingCountry("IND")
                .billingState("BR").billingZipcode("845401").transactionType(TransactionType.VOID)
                .transactionStatus(TransactionStatus.SUCCESS).createdTimestamp(ZonedDateTime.now(ZoneOffset.UTC).toString())
                .createdBy(user.getMgmId()).mgmCorrelationId("correlation123").mgmJourneyId("journey123")
                .sessionId("session123").build();
    }

    public PaymentSession createMockSession() throws IOException{
        return (PaymentSession) createRequest("/sessionResponse.json", PaymentSession.class);
    }
}
