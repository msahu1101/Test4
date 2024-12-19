package com.mgm.payments.processing.service.service.processor;


import brave.Tracer;
import com.mgm.payments.processing.service.MockRequestCreator;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.external.ClientConfigurationServiceCaller;
import com.mgm.payments.processing.service.external.PaymentRouterServiceCaller;
import com.mgm.payments.processing.service.external.SessionServiceCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfig;
import com.mgm.payments.processing.service.model.payload.clientconfig.ClientConfigPayload;
import com.mgm.payments.processing.service.model.payload.pps.GatewayResult;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.model.payload.router.*;
import com.mgm.payments.processing.service.model.payload.session.PaymentSession;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.refund.RefundRedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.util.SnowFlakeSequenceGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;
import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.SUCCESS_RESPONSE_STATUS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = RefundPaymentProcessorTest.class)
class RefundPaymentProcessorTest {

    @InjectMocks
    @Spy
    RefundPaymentProcessor refundPaymentProcessor;

    @Mock
    PaymentProcessingRepositoryWrapper repository;

    @Mock
    private PaymentRouterServiceCaller routerServiceCaller;

    @Mock
    private RefundRedisPaymentRepositoryWrapper refundRedisPaymentRepositoryWrapper;

    @Mock
    AuditMapper auditMapper;

    @Mock
    ClientConfigurationServiceCaller clientConfigurationServiceCaller;
    @Mock
    private Tracer tracer;
    @Mock
    SessionServiceCaller sessionServiceCaller;
    @Mock
    private SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;

    private User user;
    private HeadersDTO headersDTO;

    MockRequestCreator mockRequestCreator = new MockRequestCreator();


    @BeforeEach
    void init() {
        user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("web", "1234", "12345", "123456", "WEB", "jwtToken", "clientId", "userAgent");
    }

    @Test
    void testProcess() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockRefundRequest();
        PaymentSession paymentSession = mockRequestCreator.createMockSession();
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        List<PaymentEntity> entityList = new ArrayList<>();
        PaymentEntity captureEntity = PaymentEntity.builder().paymentId("123").gatewayId("SHFT")
                .last4DigitsOfTheCard("7890").build();
        entityList.add(captureEntity);
        PaymentEntity refundEntity = PaymentEntity.builder().paymentId("123").gatewayId("SHFT")
                .last4DigitsOfTheCard("7890").build();
        Mockito.doNothing().when(refundPaymentProcessor).
                        populatePaymentEntity(headersDTO, paymentRequest, entityList,refundEntity, user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        Mockito.doReturn(Mono.just(new ResponseEntity(paymentRouterResponse, HttpStatus.OK))).when(routerServiceCaller)
                .invokeRouter(any(), any(), any());
        Mockito.when(refundPaymentProcessor.invokePaymentRouter(refundEntity, paymentRequest, headersDTO))
                .thenReturn(Mono.just(paymentRouterResponse));
        PaymentResponse paymentResponse =  new PaymentResponse();
        Mockito.doNothing().when(refundPaymentProcessor).mapPaymentRouterResponseToPaymentResponse(refundEntity,
                paymentRequest, paymentRouterResponse, paymentResponse, headersDTO);
        Mockito.when(clientConfigurationServiceCaller.getClientConfig(headersDTO))
                .thenReturn(getClientConfigPayload1());
        Mockito.doReturn(paymentSession).when(refundPaymentProcessor).retrieveClientDetailsFromSession(paymentRequest, headersDTO);
        Mockito.doReturn(paymentSession).when(sessionServiceCaller).retrieveSession(headersDTO, "e92f3788-0d79-4e13-aa1c-73a38b80b2eb");
        Mockito.doNothing().when(refundPaymentProcessor).validateDuplicateRequestFromDB(paymentRequest, headersDTO);
        Mono<PaymentResponse> paymentResponseMono = refundPaymentProcessor.process(paymentRequest, user, headersDTO);
        paymentResponse = paymentResponseMono.block();
        assertNotNull(paymentResponse);
        assertNotNull(paymentResponse.getResults());
        assertEquals(TransactionStatus.SUCCESS.name(), paymentResponse.getResults().get(0).getGatewayResult().getTransactionStatus());

    }

    @Test
    void testProcessThrowsException() {
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        List entityList = new ArrayList<>();
        Mockito.when(repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO)).
                thenReturn(entityList);
        Mockito.doNothing().when(refundPaymentProcessor).
                        populatePaymentEntity(any(), any(), any(), any(), any());
        Mockito.doThrow(new RuntimeException())
                .when(refundPaymentProcessor).invokePaymentRouter(Mockito.any(PaymentEntity.class), Mockito.any(PaymentRequest.class),
                        Mockito.any(HeadersDTO.class));
        Assertions.assertThrows(RuntimeException.class, () ->
                refundPaymentProcessor.process(paymentRequest, user, headersDTO));

    }
    @Test
    void testValidateInputRequest() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockRefundRequest();
        PaymentEntity refundEntity = mockRequestCreator.createMockRefundEntity(headersDTO, user);
        paymentRequest.setPaymentId(refundEntity.getPaymentId());
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        PaymentEntity captureEntity = mockRequestCreator.
                createMockCapturePaymentEntity(headersDTO,user);
        authEntity.setPaymentId("payment-id");
        List<PaymentEntity> paymentList = Arrays.asList(authEntity, captureEntity);
        refundPaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
        );

    }

    @Test
    void testValidateInputRequestThrowsException() throws IOException {
        Mockito.when(clientConfigurationServiceCaller.getClientConfig(headersDTO))
                .thenReturn(getClientConfigPayload());
        PaymentRequest paymentRequest = mockRequestCreator.createMockRefundRequest();
        PaymentEntity refundEntity = mockRequestCreator.createMockRefundEntity(headersDTO, user);
        paymentRequest.setPaymentId(refundEntity.getPaymentId());
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        PaymentEntity captureEntity = mockRequestCreator.
                createMockCapturePaymentEntity(headersDTO,user);
        authEntity.setAmount(new BigDecimal(40));
        captureEntity.setAmount(new BigDecimal(40));
        authEntity.setAuthorizedAmount(new BigDecimal(40));
        captureEntity.setAuthorizedAmount(new BigDecimal(40));
        List<PaymentEntity> paymentList = Arrays.asList(authEntity, captureEntity);
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, ()->
        refundPaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
        ));
        assertEquals("Refund amount cannot be greater than Captured amount  !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00050", e.getExceptionResponse().getErrorCode());
        assertEquals(412, e.getHttpStatus().value());

    }


    @Test
    void testSaveInputRecordInProcess() throws IOException {
        PaymentRequest paymentRequest =mockRequestCreator.createMockRefundRequest();
        paymentRequest.setClientReferenceNumber("order-ref");
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        PaymentEntity captureEntity = mockRequestCreator.
                createMockCapturePaymentEntity(headersDTO,user);
        captureEntity.setLast4DigitsOfTheCard(authEntity.getLast4DigitsOfTheCard());
        authEntity.setPaymentId("payment-id");
        List<PaymentEntity> paymentList = Arrays.asList(authEntity, captureEntity);
        PaymentEntity refundPaymentEntity = mockRequestCreator.createMockRefundEntity(headersDTO, user);
        refundPaymentProcessor.populatePaymentEntity(headersDTO,paymentRequest,paymentList , refundPaymentEntity, user);
        assertNotNull(refundPaymentEntity.getPaymentId());
        assertEquals(paymentRequest.getAmount().get(0).getValue(),refundPaymentEntity.getAmount());
        assertEquals(TransactionType.REFUND, refundPaymentEntity.getTransactionType());
        assertEquals(paymentRequest.getClientReferenceNumber(), refundPaymentEntity.getClientReferenceNumber());
        assertEquals(TransactionStatus.IN_PROCESS, refundPaymentEntity.getTransactionStatus());
        assertNotNull(refundPaymentEntity.getCreatedTimestamp());
        assertNotNull(refundPaymentEntity.getUpdatedTimestamp());
        assertNull( refundPaymentEntity.getGatewayChainId());
        assertEquals(headersDTO.getMgmCorrelationId(), refundPaymentEntity.getMgmCorrelationId());
        assertEquals(headersDTO.getMgmJourneyId(), refundPaymentEntity.getMgmJourneyId());
        assertEquals(paymentRequest.getSessionId(),refundPaymentEntity.getSessionId());
    }

    @Test
    void testInvokePaymentRouter() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockRefundRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        authorizePaymentEntity.setAmount(paymentRequest.getAmount().get(0).getValue());
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, capturePaymentEntity));

        ResponseEntity<PaymentRouterResponse> expectedResponse = new ResponseEntity<>(mockRequestCreator.createMockRouterResponse()
                , HttpStatus.OK);
        Mockito.when(routerServiceCaller.invokeRouter(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(expectedResponse));
        PaymentRouterResponse paymentRouterResponse = refundPaymentProcessor.invokePaymentRouter(paymentList.get(1), paymentRequest,
                headersDTO).block();
        assertEquals(Objects.requireNonNull(expectedResponse.getBody()).getResults().get(0).getGatewayResult().getRouterFunction(),
                paymentRouterResponse.getResults().get(0).getGatewayResult().getRouterFunction());

    }

    @Test
    void testUpdateRouterResponseInTheDBRecord() throws IOException {
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        authEntity.setPaymentId("payment-id");
        PaymentEntity refundRecord = mockRequestCreator.createMockRefundEntity(headersDTO, user);
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        refundPaymentProcessor.updateRouterResponseInTheDBRecord(refundRecord,prResponse, user, headersDTO, null);
        Mockito.verify(repository).save(refundRecord, headersDTO);
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        assertEquals(gatewayResult.getCard().getGatewayId(),refundRecord.getGatewayId());
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(),refundRecord.getPaymentAuthId());
        assertEquals("8889", refundRecord.getLast4DigitsOfTheCard());
        assertEquals(prResponse.getResults().get(0).getGatewayResult().getAmount().get(3).getValue(),
                refundRecord.getAuthorizedAmount());
        assertEquals( TransactionStatus.SUCCESS, refundRecord.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), refundRecord.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), refundRecord.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), refundRecord.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), refundRecord.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), refundRecord.getDeferredAuth());
        assertNotNull(refundRecord.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), refundRecord.getUpdatedBy());
    }

    @Test
    void testUpdateRouterResponseInTheDBRecord_Error() throws IOException {
        PaymentEntity refundRecord =mockRequestCreator.
                createMockRefundEntity(headersDTO,user);
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterErrorResponse();
        refundPaymentProcessor.updateRouterResponseInTheDBRecord(refundRecord, prResponse, user, headersDTO, null);
        verify(repository).save(refundRecord, headersDTO);
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        assertEquals(gatewayResult.getCard().getGatewayId(),refundRecord.getGatewayId());
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(),refundRecord.getPaymentAuthId());
        assertEquals(gatewayResult.getCard().getTenderDisplay(), refundRecord.getLast4DigitsOfTheCard());
        assertEquals( new BigDecimal(0),
                refundRecord.getAuthorizedAmount());
        assertEquals( TransactionStatus.FAILURE, refundRecord.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), refundRecord.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), refundRecord.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), refundRecord.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), refundRecord.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), refundRecord.getDeferredAuth());
        assertNotNull(refundRecord.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), refundRecord.getUpdatedBy());
    }

    @Test
    void testMapPaymentRouterResponseToPaymentResponse() throws IOException {
        PaymentEntity refundRecord =mockRequestCreator.
                createMockRefundEntity(headersDTO,user);
        String paymentId = refundRecord.getPaymentId();
        PaymentRouterResponse paymentRouterResponse =  mockRequestCreator.createMockRouterResponse();
        PaymentRequest mockRefundRequest = mockRequestCreator.createMockRefundRequest();
        PaymentResponse paymentResponse = new PaymentResponse();
        refundPaymentProcessor.mapPaymentRouterResponseToPaymentResponse(refundRecord,
                mockRefundRequest, paymentRouterResponse, paymentResponse, headersDTO);
        Assertions.assertEquals(paymentId, paymentResponse.getResults().get(0).getGatewayResult().getTransaction().getPaymentId());
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        assertEquals(paymentId, gatewayResult.getTransaction().getPaymentId());
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getCard().getTenderDisplay(),paymentResponse.getResults().get(0).getGatewayResult().getCard().getMaskedCardNumber());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals( TransactionStatus.SUCCESS.toString(), gatewayResult.getTransactionStatus());
        assertEquals("001", gatewayResult.getTransactionCode());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());
    }

    private ClientConfigPayload getClientConfigPayload() {
        ClientConfigPayload clientConfigPayload = new ClientConfigPayload();
        clientConfigPayload.setClientId("clientId");
        List<ClientConfig> clientConfigList = new ArrayList<>();
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setConfigName("PaymentConfigs");
        Map<String, Boolean> configMap = new HashMap<>();
        configMap.put("isAdhocRefundAllowed", true);
        clientConfig.setConfigValue(configMap);
        clientConfigList.add(clientConfig);
        clientConfigPayload.setConfigDetails(clientConfigList);
        return clientConfigPayload;
    }

    private ClientConfigPayload getClientConfigPayload1() {
        ClientConfigPayload clientConfigPayload = new ClientConfigPayload();
        clientConfigPayload.setClientId("clientId");
        List<ClientConfig> clientConfigList = new ArrayList<>();
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setConfigName("PaymentConfigs");
        Map<String, Boolean> configMap = new HashMap<>();
        configMap.put("adhocRefund", true);
        clientConfig.setConfigValue(configMap);
        clientConfigList.add(clientConfig);
        clientConfigPayload.setConfigDetails(clientConfigList);
        return clientConfigPayload;
    }

}
