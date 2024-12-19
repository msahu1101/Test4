package com.mgm.payments.processing.service.service.processor;


import brave.Tracer;
import com.mgm.payments.processing.service.MockRequestCreator;
import com.mgm.payments.processing.service.entity.jpa.PaymentEntity;
import com.mgm.payments.processing.service.enums.*;
import com.mgm.payments.processing.service.exception.PaymentProcessingException;
import com.mgm.payments.processing.service.external.PaymentRouterServiceCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.*;
import com.mgm.payments.processing.service.model.payload.pps.GatewayResult;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.model.payload.router.*;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.payment.RedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.voidcall.VoidRedisPaymentRepositoryWrapper;
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

@SpringBootTest(classes = VoidPaymentProcessorTest.class)
class VoidPaymentProcessorTest {

    @InjectMocks
    @Spy
    VoidPaymentProcessor voidPaymentProcessor;

    @Mock
    PaymentProcessingRepositoryWrapper repository;

    @Mock
    RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper;

    @Mock
    VoidRedisPaymentRepositoryWrapper voidRedisPaymentRepositoryWrapper;
    @Mock
    private PaymentRouterServiceCaller routerServiceCaller;
    @Mock
    private SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;

    MockRequestCreator mockRequestCreator = new MockRequestCreator();

    @Mock
    AuditMapper auditMapper;
    @Mock
    private Tracer tracer;

    private User user;
    private HeadersDTO headersDTO;

    @BeforeEach
    void init() {
        user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("web", "1234", "12345", "123456", "WEB", "jwtToken", "clientId", "userAgent");
    }
    @Test
    void testProcess() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        List entityList = new ArrayList<>();
        Mockito.when(repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO)).
                thenReturn(entityList);
        PaymentEntity captureEntity = new PaymentEntity();
        Mockito.doNothing()
                .when(voidPaymentProcessor).populatePaymentEntity(any(), any(), any(), any(), any());
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterErrorResponse();
        Mockito.doReturn(Mono.just(paymentRouterResponse))
                .when(voidPaymentProcessor).invokePaymentRouter(any(), any(), any());
        Mockito.doNothing()
                .when(voidPaymentProcessor).validateInputRequest( paymentRequest, entityList, headersDTO);
        Mockito.doCallRealMethod().when(voidPaymentProcessor).updateRouterResponseInTheDBRecord(captureEntity,paymentRouterResponse, user, headersDTO, entityList);
        voidPaymentProcessor.process(paymentRequest, user, headersDTO);
        Mockito.verify(voidPaymentProcessor).validateInputRequest(paymentRequest, entityList, headersDTO);
        Mockito.verify(voidPaymentProcessor).
                populatePaymentEntity(headersDTO, paymentRequest, entityList,captureEntity, user);
        Mockito.verify(voidPaymentProcessor).invokePaymentRouter(captureEntity, paymentRequest, headersDTO);
    }

    @Test
    void testVoidThrowsException(){
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        List entityList = new ArrayList<>();
        Mockito.when(repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO)).
                thenReturn(entityList);
        PaymentEntity voidEntity = new PaymentEntity();
        Mockito.doNothing().when(voidPaymentProcessor).
                        populatePaymentEntity(any(), any(), any(), any(), any());
        PaymentRouterResponse paymentRouterResponse = Mockito.mock(PaymentRouterResponse.class);
        Mockito.doThrow(new RuntimeException())
                .when(voidPaymentProcessor).invokePaymentRouter(voidEntity, paymentRequest, headersDTO);
        Mockito.doNothing().when(voidPaymentProcessor).validateInputRequest(paymentRequest, entityList, headersDTO);
        Assertions.assertThrows(RuntimeException.class, () ->
                voidPaymentProcessor.process(paymentRequest, user, headersDTO));
        Mockito.verify(voidPaymentProcessor).validateInputRequest(paymentRequest, entityList, headersDTO);
        Mockito.verify(voidPaymentProcessor).
                populatePaymentEntity(headersDTO, paymentRequest, entityList, voidEntity, user);
        Mockito.verify(voidPaymentProcessor).invokePaymentRouter(voidEntity, paymentRequest, headersDTO);
        Mockito.verify(voidPaymentProcessor, Mockito.times(0))
                .updateRouterResponseInTheDBRecord(voidEntity, paymentRouterResponse, user, headersDTO, entityList);
        Mockito.verify(voidPaymentProcessor, Mockito.times(0))
                .mapPaymentRouterResponseToPaymentResponse(any(), any(), any(), any(), any());
        Mockito.verify(voidPaymentProcessor).updateDBOnFailure(any(), any(), any());
    }
    @Test
    void testValidateInputRequest(){
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity VoidPaymentEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        VoidPaymentEntity.setGatewayChainId("some value");
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, VoidPaymentEntity));
        voidPaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO);
    }

    @Test
    void testValidateInputRequestThrowsVoidAlreadyExistsException(){
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity voidPaymentEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, voidPaymentEntity));
        voidPaymentEntity.setReferenceId(authorizePaymentEntity.getPaymentId());
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                voidPaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO));
        assertEquals("Transaction is already void!! Duplicate Void Call !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00110", e.getExceptionResponse().getErrorCode());
    }


    @Test
    void testValidateInputRequestVoidException(){
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity voidEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        voidEntity.setReferenceId(authorizePaymentEntity.getPaymentId());
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, voidEntity));
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                voidPaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO));
        assertEquals("Transaction is already void!! Duplicate Void Call !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00110", e.getExceptionResponse().getErrorCode());
    }


    @Test
    void testSaveInputRecordInProcess(){
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        PaymentEntity authPaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        List<PaymentEntity> paymentList = Arrays.asList(authPaymentEntity);
        paymentRequest.setPaymentId(authPaymentEntity.getPaymentId());
        PaymentEntity voidPaymentEntity = new PaymentEntity();
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        voidPaymentProcessor.populatePaymentEntity(headersDTO, paymentRequest, paymentList, voidPaymentEntity, user);
        voidPaymentEntity.setClientReferenceNumber("101");
        paymentRequest.setClientReferenceNumber("101");
        assertNotNull(voidPaymentEntity.getPaymentId());
        assertArrayEquals(new String[]{authPaymentEntity.getClientReferenceNumber(), authPaymentEntity.getPaymentId(),
                        authPaymentEntity.getClientId(), authPaymentEntity.getOrderType(), authPaymentEntity.getMgmId()},
                new String[]{voidPaymentEntity.getClientReferenceNumber(), voidPaymentEntity.getReferenceId(),
                        voidPaymentEntity.getClientId(), voidPaymentEntity.getOrderType(),
                        voidPaymentEntity.getMgmId()});

        assertEquals(authPaymentEntity.getAmount(), voidPaymentEntity.getAmount());
        assertArrayEquals(new String[]{authPaymentEntity.getCardHolderName(),
                        authPaymentEntity.getTenderType(), authPaymentEntity.getLast4DigitsOfTheCard(),
                        authPaymentEntity.getIssuerType(), authPaymentEntity.getCurrencyCode().toString(),
                        authPaymentEntity.getBillingAddress1(), authPaymentEntity.getBillingAddress2(), authPaymentEntity.getBillingCity(),
                        authPaymentEntity.getBillingState(), authPaymentEntity.getBillingZipcode(),
                        authPaymentEntity.getBillingCountry()},
                new String[]{voidPaymentEntity.getCardHolderName(),
                        voidPaymentEntity.getTenderType(), voidPaymentEntity.getLast4DigitsOfTheCard(),
                        voidPaymentEntity.getIssuerType(), voidPaymentEntity.getCurrencyCode().toString(),
                        voidPaymentEntity.getBillingAddress1(), voidPaymentEntity.getBillingAddress2(),
                        voidPaymentEntity.getBillingCity(),
                        voidPaymentEntity.getBillingState(), voidPaymentEntity.getBillingZipcode(),
                        voidPaymentEntity.getBillingCountry()});
        assertArrayEquals(new String[]{authPaymentEntity.getGatewayId(),
                         authPaymentEntity.getGatewayChainId()},
                new String[]{voidPaymentEntity.getGatewayId(),
                        voidPaymentEntity.getGatewayChainId()});

        assertEquals(TransactionType.VOID, voidPaymentEntity.getTransactionType());
        assertEquals(paymentRequest.getClientReferenceNumber(), voidPaymentEntity.getClientReferenceNumber());
        assertEquals(TransactionStatus.IN_PROCESS, voidPaymentEntity.getTransactionStatus());
        assertNotNull(voidPaymentEntity.getCreatedTimestamp());
        assertNotNull(voidPaymentEntity.getUpdatedTimestamp());
        assertEquals(authPaymentEntity.getMgmId(), voidPaymentEntity.getMgmId());
        assertEquals(headersDTO.getMgmCorrelationId(), voidPaymentEntity.getMgmCorrelationId());
        assertEquals(headersDTO.getMgmJourneyId(), voidPaymentEntity.getMgmJourneyId());
        assertEquals(paymentRequest.getSessionId(), voidPaymentEntity.getSessionId());
    }

    @Test
    void testInvokePaymentRouter() throws IOException {
        PaymentEntity VoidPaymentEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        ResponseEntity<PaymentRouterResponse> expectedResponse = new ResponseEntity<>(mockRequestCreator.createMockRouterResponse(), HttpStatus.OK);
        Mockito.when(routerServiceCaller.invokeRouter(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(expectedResponse));
        PaymentRouterResponse paymentRouterResponse = voidPaymentProcessor.invokePaymentRouter(VoidPaymentEntity, paymentRequest, headersDTO).block();
        assertEquals(Objects.requireNonNull(expectedResponse.getBody()).getResults().get(0).getGatewayResult().getRouterFunction(),
                paymentRouterResponse.getResults().get(0).getGatewayResult().getRouterFunction());
    }


    @Test
    void testUpdateRouterResponseInTheDBRecord() throws IOException {
        PaymentEntity VoidPaymentRec = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        voidPaymentProcessor.updateRouterResponseInTheDBRecord(VoidPaymentRec, prResponse, user, headersDTO, Collections.singletonList(VoidPaymentRec));
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(), VoidPaymentRec.getPaymentAuthId());
        assertEquals(prResponse.getResults().get(0).getGatewayResult().getAmount().get(3).getValue(),
                VoidPaymentRec.getAuthorizedAmount());
        assertEquals(TransactionStatus.SUCCESS, VoidPaymentRec.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), VoidPaymentRec.getGatewayTransactionStatusCode());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), VoidPaymentRec.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), VoidPaymentRec.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), VoidPaymentRec.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), VoidPaymentRec.getDeferredAuth());
        assertNotNull(VoidPaymentRec.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), VoidPaymentRec.getUpdatedBy());
    }

    @Test
    void testUpdateRouterResponseInTheDBRecord_Error() throws IOException {
        PaymentEntity VoidPaymentRec = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        prResponse.getResults().get(0).getGatewayResult().getTransaction().setResponseCode(RouterResponseCode.F.toString());
        voidPaymentProcessor.updateRouterResponseInTheDBRecord(VoidPaymentRec, prResponse, user, headersDTO, Collections.singletonList(VoidPaymentRec));
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        gatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(), VoidPaymentRec.getPaymentAuthId());
        assertEquals( new BigDecimal(0),
                VoidPaymentRec.getAuthorizedAmount());
        assertEquals(TransactionStatus.FAILURE, VoidPaymentRec.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), VoidPaymentRec.getGatewayTransactionStatusCode());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), VoidPaymentRec.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), VoidPaymentRec.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), VoidPaymentRec.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), VoidPaymentRec.getDeferredAuth());
        assertNotNull(VoidPaymentRec.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), VoidPaymentRec.getUpdatedBy());
    }

    @Test
    void testMapPaymentRouterResponseToPaymentResponse() throws IOException {
        PaymentEntity voidPaymentEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        PaymentResponse paymentResponse = new PaymentResponse();
                voidPaymentProcessor.mapPaymentRouterResponseToPaymentResponse(voidPaymentEntity,
                mockRequestCreator.createMockVoidRequest(), paymentRouterResponse, paymentResponse, headersDTO);
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());
    }

    @Test
    void testMapPaymentRouterResponseToPaymentResponse_Error() throws IOException {
        PaymentEntity voidEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        routerGatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());
        PaymentResponse paymentResponse = new PaymentResponse();
        voidPaymentProcessor.mapPaymentRouterResponseToPaymentResponse(voidEntity,
                mockRequestCreator.createMockVoidRequest(), paymentRouterResponse, paymentResponse, headersDTO);
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());

    }


}
