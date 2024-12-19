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
import com.mgm.payments.processing.service.repository.redis.capture.CaptureRedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.payment.RedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.util.PaymentProcessingUtil;
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

@SpringBootTest(classes = CapturePaymentProcessorTest.class)
class CapturePaymentProcessorTest {

    @InjectMocks
    @Spy
    CapturePaymentProcessor capturePaymentProcessor;

    @Mock
    PaymentProcessingRepositoryWrapper repository;
    @Mock
    private Tracer tracer;

    @Mock
    private PaymentRouterServiceCaller routerServiceCaller;

    @Mock
    CaptureRedisPaymentRepositoryWrapper captureRedisPaymentRepositoryWrapper;

    @Mock
    RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper;

    MockRequestCreator mockRequestCreator = new MockRequestCreator();

    @Mock
    CaptureConfirmEventListener captureConfirmEventService;
    @Mock
    private SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;


    @Mock
    AuditMapper auditMapper;

    private User user;
    private HeadersDTO headersDTO;

    @BeforeEach
    void init() {
        user = new User("00u1tdaotm5Y20maD0h8", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("pamService", "1234", "12345", "123456", "WEB", "jwtToken", "clientId", "userAgent");
    }

    @Test
    void testValidateInputRequest() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        authorizePaymentEntity.setAmount(paymentRequest.getAmount().get(0).getValue());
        authorizePaymentEntity.setAuthorizedAmount(paymentRequest.getAmount().get(0).getValue());
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        capturePaymentEntity.setGatewayChainId("some value");
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, capturePaymentEntity));
        capturePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO);
    }

    @Test
    void testValidateInputRequestThrowsCaptureAlreadyExistsException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        authorizePaymentEntity.setAmount(paymentRequest.getAmount().get(0).getValue());
        authorizePaymentEntity.setAuthorizedAmount(paymentRequest.getAmount().get(0).getValue());
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        capturePaymentEntity.setReferenceId(authorizePaymentEntity.getPaymentId());
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, capturePaymentEntity));
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                capturePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
                ));
        assertEquals("Looks like Capture was already made for this payment id !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00080", e.getExceptionResponse().getErrorCode());
        assertEquals(412, e.getHttpStatus().value());


    }

    @Test
    void testValidateInputRequestAmountException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        capturePaymentEntity.setGatewayChainId("some value");
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, capturePaymentEntity));
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                capturePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
                ));
        assertEquals("The Capture total Amount must be equal to the total Authorized Amount !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00100", e.getExceptionResponse().getErrorCode());
        assertEquals(412, e.getHttpStatus().value());

    }

    @Test
    void testValidateInputRequestAmountAuthorizeDoesNotExistException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        capturePaymentEntity.setGatewayChainId("some value");
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                capturePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
                ));
        assertEquals("Capture cannot happen without authorize !!Authorize do not exist for this capture !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00030", e.getExceptionResponse().getErrorCode());
        assertEquals(412, e.getHttpStatus().value());

    }

    @Test
    void testValidateInputRequestVoidException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List<PaymentEntity> paymentList = new ArrayList<>();
        PaymentEntity authorizePaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        authorizePaymentEntity.setAmount(paymentRequest.getAmount().get(0).getValue());
        authorizePaymentEntity.setAuthorizedAmount(paymentRequest.getAmount().get(0).getValue());
        paymentRequest.setPaymentId(authorizePaymentEntity.getPaymentId());
        PaymentEntity voidEntity = mockRequestCreator.createMockVoidEntity(headersDTO, user);
        voidEntity.setReferenceId(authorizePaymentEntity.getPaymentId());
        paymentList.addAll(Arrays.asList(authorizePaymentEntity, voidEntity));
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                capturePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
                ));
        assertEquals("This PaymentId has been voided already, and it cannot be Captured!!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00090", e.getExceptionResponse().getErrorCode());
        assertEquals(412, e.getHttpStatus().value());
    }

    @Test
    void testProcess() throws Exception {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List<PaymentEntity> entityList = new ArrayList<>();
        entityList.add(mockRequestCreator.createAuthPaymentEntity(headersDTO,user));
        Mockito.when(repository.findByPaymentIdOrReferenceId(paymentRequest.getPaymentId(), paymentRequest.getPaymentId(), headersDTO)).
                thenReturn(entityList);
        PaymentEntity captureEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO,user);
        captureEntity.setTransactionStatus(TransactionStatus.SUCCESS);
        Mockito.doNothing().when(capturePaymentProcessor).
                        populatePaymentEntity(any(), any(), any(), any(), any());
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        Mockito.doReturn(Mono.just(paymentRouterResponse)).when(capturePaymentProcessor)
                .invokePaymentRouter(captureEntity, paymentRequest, headersDTO);
        Mockito.doReturn(Mono.just(new ResponseEntity(paymentRouterResponse, HttpStatus.OK))).when(routerServiceCaller)
                .invokeRouter(any(), any(), any());
        Mockito.doNothing().when(capturePaymentProcessor).validateInputRequest(any(),any(), any());
        Mockito.doNothing().when(capturePaymentProcessor).updateRouterResponseInTheDBRecord(captureEntity, paymentRouterResponse, user, headersDTO, entityList);
        Mono<PaymentResponse> paymentResponseMono = capturePaymentProcessor.process(paymentRequest, user, headersDTO);
        PaymentResponse paymentResponse = paymentResponseMono.block();

        assertNotNull(paymentResponse);
        assertNotNull(paymentResponse.getResults());
        assertEquals(TransactionStatus.SUCCESS.name(), paymentResponse.getResults().get(0).getGatewayResult().getTransactionStatus());
    }

    @Test
    void testProcessThrowsException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        List entityList = new ArrayList<>();
        entityList.add(mockRequestCreator.createAuthPaymentEntity(headersDTO, user));
        Mockito.when(repository.findByPaymentIdOrReferenceId(paymentRequest.getPaymentId(), paymentRequest.getPaymentId(), headersDTO)).
                thenReturn(entityList);
        PaymentEntity captureEntity = new PaymentEntity();
        Mockito.doNothing().when(capturePaymentProcessor).
                        populatePaymentEntity(any(), any(), any(),any(), any());
        Mockito.doNothing().when(capturePaymentProcessor).validateInputRequest(any(), any(), any());
        PaymentRouterResponse paymentRouterResponse = Mockito.mock(PaymentRouterResponse.class);
        Mockito.doThrow(new RuntimeException()).when(capturePaymentProcessor)
                .invokePaymentRouter(captureEntity, paymentRequest, headersDTO);
        Assertions.assertThrows(RuntimeException.class, () ->
                capturePaymentProcessor.process(paymentRequest, user, headersDTO));
        Mockito.verify(capturePaymentProcessor).validateInputRequest(paymentRequest, entityList, headersDTO);
        Mockito.verify(capturePaymentProcessor).
                populatePaymentEntity(any(), any(), any(),any(), any());
        Mockito.verify(capturePaymentProcessor).invokePaymentRouter(captureEntity, paymentRequest, headersDTO);
        Mockito.verify(capturePaymentProcessor, Mockito.times(0)).updateRouterResponseInTheDBRecord(captureEntity, paymentRouterResponse, user, headersDTO, entityList);
        Mockito.verify(capturePaymentProcessor, Mockito.times(0)).mapPaymentRouterResponseToPaymentResponse(any(), any(), any(), any(), any());
        Mockito.verify(capturePaymentProcessor).updateDBOnFailure(any(), any(), any());
    }

    @Test
    void testSaveInputRecordInProcess() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        PaymentEntity authPaymentEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO, user);
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        List<PaymentEntity> paymentList = Arrays.asList(authPaymentEntity);
        paymentRequest.setPaymentId(authPaymentEntity.getPaymentId());
        PaymentEntity capturePaymentEntity = new PaymentEntity();
        capturePaymentProcessor.populatePaymentEntity(headersDTO, paymentRequest, paymentList,capturePaymentEntity, user);
        capturePaymentEntity.setClientReferenceNumber("101");
        paymentRequest.setClientReferenceNumber("101");
        assertNotNull(capturePaymentEntity.getPaymentId());
        assertArrayEquals(new String[]{authPaymentEntity.getClientReferenceNumber(), authPaymentEntity.getPaymentId(),
                        authPaymentEntity.getClientId(), authPaymentEntity.getOrderType(), authPaymentEntity.getMgmId()},
                new String[]{capturePaymentEntity.getClientReferenceNumber(), capturePaymentEntity.getReferenceId(),
                        capturePaymentEntity.getClientId(), capturePaymentEntity.getOrderType(), capturePaymentEntity.getMgmId()});
        assertEquals(PaymentProcessingUtil.getAmount(paymentRequest.getAmount()), capturePaymentEntity.getAmount());
        assertArrayEquals(new String[]{authPaymentEntity.getCardHolderName(),
                        authPaymentEntity.getTenderType(), authPaymentEntity.getLast4DigitsOfTheCard(),
                        authPaymentEntity.getIssuerType(), authPaymentEntity.getCurrencyCode().toString(),
                        authPaymentEntity.getBillingAddress1(), authPaymentEntity.getBillingAddress2(), authPaymentEntity.getBillingCity(),
                        authPaymentEntity.getBillingState(), authPaymentEntity.getBillingZipcode(),
                        authPaymentEntity.getBillingCountry()},
                new String[]{capturePaymentEntity.getCardHolderName(),
                        capturePaymentEntity.getTenderType(), capturePaymentEntity.getLast4DigitsOfTheCard(),
                        capturePaymentEntity.getIssuerType(), capturePaymentEntity.getCurrencyCode().toString(),
                        capturePaymentEntity.getBillingAddress1(), capturePaymentEntity.getBillingAddress2(),
                        capturePaymentEntity.getBillingCity(),
                        capturePaymentEntity.getBillingState(), capturePaymentEntity.getBillingZipcode(),
                        capturePaymentEntity.getBillingCountry()});
        assertArrayEquals(new String[]{authPaymentEntity.getGatewayId()
                        , authPaymentEntity.getGatewayChainId()},
                new String[]{capturePaymentEntity.getGatewayId()
                      , capturePaymentEntity.getGatewayChainId()});
        assertEquals(TransactionType.CAPTURE, capturePaymentEntity.getTransactionType());
        assertEquals(paymentRequest.getClientReferenceNumber(), capturePaymentEntity.getClientReferenceNumber());
        assertEquals(TransactionStatus.IN_PROCESS, capturePaymentEntity.getTransactionStatus());
        assertNotNull(capturePaymentEntity.getCreatedTimestamp());
        assertNotNull(capturePaymentEntity.getUpdatedTimestamp());
        assertEquals(paymentRequest.getMgmId(), capturePaymentEntity.getMgmId());
        assertEquals(headersDTO.getMgmCorrelationId(), capturePaymentEntity.getMgmCorrelationId());
        assertEquals(headersDTO.getMgmJourneyId(), capturePaymentEntity.getMgmJourneyId());
        assertEquals(paymentRequest.getSessionId(), capturePaymentEntity.getSessionId());
    }

    @Test
    void testInvokePaymentRouter() throws IOException {
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        ResponseEntity<PaymentRouterResponse> expectedResponse = new ResponseEntity<>(mockRequestCreator.createMockRouterResponse(), HttpStatus.OK);
        Mockito.when(routerServiceCaller.invokeRouter(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Mono.just(expectedResponse));
        PaymentRouterResponse paymentRouterResponse = capturePaymentProcessor.invokePaymentRouter(capturePaymentEntity, paymentRequest, headersDTO).block();
        assertEquals(Objects.requireNonNull(expectedResponse.getBody()).getResults().get(0).getGatewayResult().getRouterFunction(),
                paymentRouterResponse.getResults().get(0).getGatewayResult().getRouterFunction());
    }


    @Test
    void testUpdateRouterResponseInTheDBRecord() throws IOException {
        PaymentEntity capturePaymentRec = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        capturePaymentProcessor.updateRouterResponseInTheDBRecord(capturePaymentRec, prResponse, user, headersDTO, new ArrayList<>());
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(), capturePaymentRec.getPaymentAuthId());
        assertEquals(prResponse.getResults().get(0).getGatewayResult().getAmount().get(3).getValue(),
                capturePaymentRec.getAuthorizedAmount());
        assertEquals(TransactionStatus.SUCCESS, capturePaymentRec.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), capturePaymentRec.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), capturePaymentRec.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), capturePaymentRec.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), capturePaymentRec.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), capturePaymentRec.getDeferredAuth());
        assertNotNull(capturePaymentRec.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), capturePaymentRec.getUpdatedBy());
    }

    @Test
    void testUpdateRouterResponseInTheDBRecord_Error() throws IOException {
        PaymentEntity capturePaymentRec = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        prResponse.getResults().get(0).getGatewayResult().getTransaction().setResponseCode(RouterResponseCode.F.toString());
        capturePaymentProcessor.updateRouterResponseInTheDBRecord(capturePaymentRec, prResponse, user, headersDTO, new ArrayList<>());
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        gatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(), capturePaymentRec.getPaymentAuthId());
        assertEquals( new BigDecimal(0),
                capturePaymentRec.getAuthorizedAmount());
        assertEquals(TransactionStatus.FAILURE, capturePaymentRec.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), capturePaymentRec.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), capturePaymentRec.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), capturePaymentRec.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), capturePaymentRec.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), capturePaymentRec.getDeferredAuth());
        assertNotNull(capturePaymentRec.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), capturePaymentRec.getUpdatedBy());
    }

    @Test
    void testMapPaymentRouterResponseToPaymentResponse() throws IOException {
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        PaymentResponse paymentResponse = new PaymentResponse();
        capturePaymentProcessor.mapPaymentRouterResponseToPaymentResponse(capturePaymentEntity,
                mockRequestCreator.createMockCaptureRequest(), paymentRouterResponse,paymentResponse, headersDTO);
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getCard().getTenderDisplay(), gatewayResult.getCard().getMaskedCardNumber());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());
    }

    @Test
    void testMapPaymentRouterResponseToPaymentResponse_Error() throws IOException {
        PaymentEntity capturePaymentEntity = mockRequestCreator.createMockCapturePaymentEntity(headersDTO, user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        routerGatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());
        PaymentResponse paymentResponse = new PaymentResponse();
        capturePaymentProcessor.mapPaymentRouterResponseToPaymentResponse(capturePaymentEntity,
                mockRequestCreator.createMockCaptureRequest(), paymentRouterResponse, paymentResponse, headersDTO);
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());

    }


}
