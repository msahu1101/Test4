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
import com.mgm.payments.processing.service.model.payload.pps.exception.PaymentExceptionResponse;
import com.mgm.payments.processing.service.model.payload.router.*;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.auth.AuthorizeRedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.repository.redis.payment.RedisPaymentRepositoryWrapper;
import com.mgm.payments.processing.service.util.SnowFlakeSequenceGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


@SpringBootTest(classes = AuthorizePaymentProcessorTest.class)
class AuthorizePaymentProcessorTest {

    @InjectMocks
    @Spy
    AuthorizePaymentProcessor authorizePaymentProcessor;

    @Mock
    PaymentProcessingRepositoryWrapper repository;

    @Mock
    AuthorizeRedisPaymentRepositoryWrapper authorizeRedisPaymentRepositoryWrapper;

    @Mock
    RedisPaymentRepositoryWrapper redisPaymentRepositoryWrapper;

    @Mock
    private PaymentRouterServiceCaller routerServiceCaller;

    @Mock
    private AuditMapper auditMapper;
    @Mock
    private Tracer tracer;
    @Mock
    private VoidPaymentProcessor voidPaymentProcessor;
    @Mock
    private SnowFlakeSequenceGenerator snowFlakeSequenceGenerator;

    MockRequestCreator mockRequestCreator = new MockRequestCreator();

    private User user;
    private HeadersDTO headersDTO;

    @BeforeEach
    void init() {
        user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("web", "1234", "12345", "123456", "WEB", "jwtToken", "clientId","userAgent");
    }

    @Test
    void testProcess() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        List<PaymentEntity> entityList = new ArrayList<>();
        PaymentEntity authEntity = new PaymentEntity();
        authEntity.setTransactionStatus(TransactionStatus.SUCCESS);
        authEntity.setTransactionType(TransactionType.AUTHORIZE);
        authEntity.setPaymentId(UUID.randomUUID().toString());
        entityList.add(authEntity);
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        Mockito.when(repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO)).
                thenReturn(entityList);
        Mockito.doNothing().when(authorizePaymentProcessor).
                        populatePaymentEntity(headersDTO, paymentRequest, entityList, authEntity, user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        Mockito.doReturn(Mono.just(paymentRouterResponse)).when(authorizePaymentProcessor)
                .invokePaymentRouter(authEntity, paymentRequest, headersDTO);
        Mockito.doReturn(Mono.just(new ResponseEntity(paymentRouterResponse, HttpStatus.OK))).when(routerServiceCaller)
                .invokeRouter(any(), any(), any());
        Mockito.doNothing().when(authorizePaymentProcessor)
                .updateRouterResponseInTheDBRecord(authEntity, paymentRouterResponse, user, headersDTO, entityList);
        PaymentResponse paymentResponse = Mockito.mock(PaymentResponse.class);
        Mockito.doNothing().when(authorizePaymentProcessor)
                .mapPaymentRouterResponseToPaymentResponse(authEntity, paymentRequest,
                        paymentRouterResponse, paymentResponse, headersDTO);
        Mono<PaymentResponse> paymentResponseMono = authorizePaymentProcessor.process(paymentRequest, user, headersDTO);
        paymentResponse = paymentResponseMono.block();
        assertNotNull(paymentResponse);
        assertNotNull(paymentResponse.getResults());
        assertEquals(TransactionStatus.SUCCESS.name(), paymentResponse.getResults().get(0).getGatewayResult().getTransactionStatus());

    }

    @Test
    void testProcessThrowsException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        List entityList = null;
        PaymentEntity authEntity = new PaymentEntity();
        Mockito.doNothing().when(authorizePaymentProcessor).
                        populatePaymentEntity(any(), any(), any(), any(), any());
        PaymentRouterResponse paymentRouterResponse = Mockito.mock(PaymentRouterResponse.class);
        Mockito.doThrow(new RuntimeException()).when(authorizePaymentProcessor)
            .invokePaymentRouter(authEntity, paymentRequest, headersDTO);
        Assertions.assertThrows(RuntimeException.class, () ->
                authorizePaymentProcessor.process(paymentRequest, user, headersDTO));
        Mockito.verify(authorizePaymentProcessor).validateInputRequest(paymentRequest, entityList, headersDTO);
        Mockito.verify(authorizePaymentProcessor).
                populatePaymentEntity(headersDTO, paymentRequest, entityList, authEntity, user);
        Mockito.verify(authorizePaymentProcessor).invokePaymentRouter(authEntity, paymentRequest, headersDTO);
        Mockito.verify(authorizePaymentProcessor, Mockito.times(0)).updateRouterResponseInTheDBRecord(authEntity, paymentRouterResponse, user, headersDTO, entityList);
        Mockito.verify(authorizePaymentProcessor, Mockito.times(0)).mapPaymentRouterResponseToPaymentResponse(any(), any(), any(), any(), any());
        Mockito.verify(authorizePaymentProcessor).updateDBOnFailure(any(), any(), any());
    }
    @Test
    void testValidateInputRequestThrowsValidationException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        paymentRequest.setPaymentId("payment-id");
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        authEntity.setPaymentId("payment-id");
        List<PaymentEntity> paymentList = Collections.singletonList(authEntity);
        PaymentProcessingException e = Assertions.assertThrows(PaymentProcessingException.class, () ->
                authorizePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
                ));
        assertEquals("Duplicate Request !! Data already present with the same details !!",
                e.getExceptionResponse().getErrorMessage());
        assertEquals("00041-0008-1-00020", e.getExceptionResponse().getErrorCode());
        assertEquals(412, e.getHttpStatus().value());

    }

    @Test
    void testValidateInputRequest() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        paymentRequest.setPaymentId("payment-id-1");
        paymentRequest.setGroupId("groupId-1");
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        authEntity.setPaymentId("payment-id");
        List<PaymentEntity> paymentList = Collections.singletonList(authEntity);
         Assertions.assertDoesNotThrow(()->
                authorizePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO));

    }

    @Test
    void testValidateInputRequestThrowsException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        PaymentEntity authEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        List<PaymentEntity> paymentList = Collections.singletonList(authEntity);
        paymentRequest.setClientReferenceNumber(paymentList.get(0).getClientReferenceNumber());
        paymentRequest.setSessionId(paymentList.get(0).getSessionId());
        paymentRequest.setAmount(Arrays.asList(new Amount("total", paymentList.get(0).getAmount())));
        paymentRequest.setPaymentId(paymentList.get(0).getPaymentId());
        Assertions.assertThrows(PaymentProcessingException.class, () ->
                authorizePaymentProcessor.validateInputRequest(paymentRequest, paymentList,
                 headersDTO));
    }

    @Test
    void testValidateInputRequestThrowsPaymentException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        List entityList = new ArrayList<>();
        Mockito.when(repository.findByClientReferenceNumber(paymentRequest.getClientReferenceNumber(), headersDTO)).
                thenReturn(entityList);
        PaymentEntity authEntity = new PaymentEntity();
        Mockito.doNothing().when(authorizePaymentProcessor).
                populatePaymentEntity(any(), any(), any(), any(), any());
        Mockito.doThrow(new PaymentProcessingException(PaymentExceptionResponse.builder().errorCode(ApiErrorCode.PAYMENT_ROUTER_EXCEPTION.getCode())
                .errorMessage(ApiErrorCode.PAYMENT_ROUTER_EXCEPTION.getDescription()).build(), HttpStatus.BAD_REQUEST)).when(authorizePaymentProcessor)
                .invokePaymentRouter(authEntity, paymentRequest, headersDTO);
        Assertions.assertThrows(PaymentProcessingException.class, () ->
                authorizePaymentProcessor.process(paymentRequest, user, headersDTO));
    }

    @Test
    void testValidateParentTransactionIdInputRequestThrowsException() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        PaymentEntity authEntity = mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        List<PaymentEntity> paymentList = Collections.singletonList(authEntity);
        paymentRequest.setClientReferenceNumber(paymentList.get(0).getClientReferenceNumber());
        paymentRequest.setSessionId(paymentList.get(0).getSessionId());
        paymentRequest.setAmount(List.of(new Amount("total", paymentList.get(0).getAmount())));
        paymentRequest.setPaymentId(paymentList.get(0).getPaymentId());
        Assertions.assertThrows(PaymentProcessingException.class, () ->
                authorizePaymentProcessor.validateInputRequest(paymentRequest, paymentList, headersDTO
                ));
    }


    @Test
    void testSaveInputRecordInProcess() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        paymentRequest.setGroupId("groupId-1");
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        authEntity.setGatewayId("SHFT1");
        authEntity.setMgmToken("12341234123412");
        PaymentEntity captureEntity = mockRequestCreator.
                createMockCapturePaymentEntity(headersDTO,user);
        captureEntity.setGatewayId("SHFT1");
        List<PaymentEntity> paymentEntityList = new ArrayList<>();
        paymentEntityList.add(authEntity);
        paymentEntityList.add(captureEntity);
        PaymentEntity savedPaymentEntity = new PaymentEntity();
        authorizePaymentProcessor.populatePaymentEntity(headersDTO, paymentRequest,
                paymentEntityList, savedPaymentEntity, user);
        BillingAddress billingAddress = BillingAddress.builder().address(savedPaymentEntity.getBillingAddress1())
                .address2(savedPaymentEntity.getBillingAddress2())
                .city(savedPaymentEntity.getBillingCity())
                .state(savedPaymentEntity.getBillingState())
                .postalCode(savedPaymentEntity.getBillingZipcode())
                .country(savedPaymentEntity.getBillingCountry()).build();
        TenderDetails expectedTenderDetails = TenderDetails.builder().mgmToken("12341234123412").
                nameOnTender("John")
                .tenderType("CREDITCARD").issuerType("BC").build();
        TenderDetails tenderDetails = TenderDetails.builder().mgmToken(savedPaymentEntity.getMgmToken()).nameOnTender(savedPaymentEntity.getCardHolderName())
                .tenderType(savedPaymentEntity.getTenderType()).issuerType( savedPaymentEntity.getIssuerType()).build();
        assertNotNull(savedPaymentEntity.getPaymentId());
        assertEquals(paymentRequest.getClientReferenceNumber(), savedPaymentEntity.getClientReferenceNumber());
        assertEquals(paymentRequest.getAmount().get(0).getValue(), savedPaymentEntity.getAmount());
        assertEquals(0, savedPaymentEntity.getAuthChainId());
        assertEquals(headersDTO.getClientId(), savedPaymentEntity.getClientId());
        assertEquals(paymentRequest.getOrderType(),savedPaymentEntity.getOrderType());
        assertEquals(paymentRequest.getMgmId(), savedPaymentEntity.getMgmId());
        assertEquals(expectedTenderDetails, tenderDetails);
        assertEquals("1212", savedPaymentEntity.getLast4DigitsOfTheCard());
        assertNull(savedPaymentEntity.getGatewayChainId());

        assertEquals(paymentRequest.getPayment().getCurrencyCode(), savedPaymentEntity.getCurrencyCode());
        assertEquals(paymentRequest.getPayment().getBillingAddress(),billingAddress);
        assertEquals(TransactionType.AUTHORIZE, savedPaymentEntity.getTransactionType());
        assertEquals(TransactionStatus.IN_PROCESS, savedPaymentEntity.getTransactionStatus());

        assertNotNull(savedPaymentEntity.getCreatedTimestamp());
        assertNotNull(savedPaymentEntity.getUpdatedTimestamp());
        assertEquals(headersDTO.getMgmCorrelationId(), savedPaymentEntity.getMgmCorrelationId());
        assertEquals(headersDTO.getMgmJourneyId(), savedPaymentEntity.getMgmJourneyId());
        assertEquals(paymentRequest.getSessionId(),savedPaymentEntity.getSessionId());
    }

    @Test
    void testSaveInputRecordInProcessNewMgmToken() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        paymentRequest.setGroupId("groupId-1");
        PaymentEntity authEntity = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        authEntity.setGatewayId("SHFT1");
        authEntity.setMgmToken("1234123412341");
        PaymentEntity captureEntity = mockRequestCreator.
                createMockCapturePaymentEntity(headersDTO,user);
        captureEntity.setGatewayId("SHFT1");
        List<PaymentEntity> paymentEntityList = new ArrayList<>();
        paymentEntityList.add(authEntity);
        paymentEntityList.add(captureEntity);
        PaymentEntity savedPaymentEntity = new PaymentEntity();
        Mockito.when(snowFlakeSequenceGenerator.nextId()).thenReturn(1234123412L);
        authorizePaymentProcessor.populatePaymentEntity(headersDTO, paymentRequest, paymentEntityList, savedPaymentEntity, user);
        assertNotNull(savedPaymentEntity.getPaymentId());
        assertEquals(paymentRequest.getClientReferenceNumber(), savedPaymentEntity.getClientReferenceNumber());
        assertEquals(paymentRequest.getMgmId(), savedPaymentEntity.getMgmId());
        assertEquals(paymentRequest.getPayment().getTenderDetails().getMgmToken(), savedPaymentEntity.getMgmToken());
        assertEquals(paymentRequest.getPayment().getTenderDetails().getNameOnTender(), savedPaymentEntity.getCardHolderName());
        assertEquals(paymentRequest.getPayment().getTenderDetails().getTenderType(), savedPaymentEntity.getTenderType());
        assertEquals("1212", savedPaymentEntity.getLast4DigitsOfTheCard());
        assertNull(savedPaymentEntity.getGatewayChainId());
        assertEquals(TransactionType.AUTHORIZE, savedPaymentEntity.getTransactionType());
        assertEquals(TransactionStatus.IN_PROCESS, savedPaymentEntity.getTransactionStatus());
    }

    @Test
    void testInvokePaymentRouter() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        PaymentEntity payment = mockRequestCreator.
                createAuthPaymentEntity(headersDTO,user);
        payment.setGatewayId("SHFT");
        Mockito.when(routerServiceCaller.invokeRouter(Mockito.any(PaymentRouterRequest.class),Mockito.any(HeadersDTO.class),Mockito.any(String.class))).
                thenReturn(Mono.just(new ResponseEntity<>(mockRequestCreator.createMockRouterResponse(), HttpStatus.OK)));
        authorizePaymentProcessor.invokePaymentRouter(payment, paymentRequest, headersDTO);
        ArgumentCaptor<PaymentRouterRequest> argumentCaptor = ArgumentCaptor.forClass(PaymentRouterRequest.class);
        ArgumentCaptor<HeadersDTO> argumentCaptorHeaders = ArgumentCaptor.forClass(HeadersDTO.class);
        ArgumentCaptor<String> paymentIdCap = ArgumentCaptor.forClass(String.class);
        verify(routerServiceCaller).invokeRouter(argumentCaptor.capture(),argumentCaptorHeaders.capture(),paymentIdCap.capture());
        PaymentRouterRequest paymentRouterRequest = argumentCaptor.getValue();
        assertEquals(paymentRequest.getClientReferenceNumber(),paymentRouterRequest.getClientReferenceNumber() );
        assertEquals(paymentRequest.getMgmId(), paymentRouterRequest.getMgmId());
        assertEquals(RouterFunction.AUTHORIZE, paymentRouterRequest.getRouterFunction());
        assertNull(paymentRouterRequest.getGatewayChainId());
        assertEquals(payment.getGatewayId(), paymentRouterRequest.getGatewayId());
        assertEquals(paymentRequest.getAmount(), paymentRouterRequest.getAmount());
        assertEquals(paymentRequest.getPayment(), paymentRouterRequest.getPayment());
        assertEquals(paymentRequest.getAdditionalAttributes(), paymentRouterRequest.getAdditionalAttributes());
    }

    @Test
    void testUpdateRouterResponseInTheDBRecord() throws IOException {
        PaymentEntity payment= mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        payment.setGroupId("groupId-1");
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        authorizePaymentProcessor.updateRouterResponseInTheDBRecord(payment,prResponse, user, headersDTO, Collections.singletonList(payment));
        verify(repository).save(payment, headersDTO);
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        assertEquals(gatewayResult.getTransaction().getGatewayChainId(),payment.getGatewayChainId());
        assertEquals(gatewayResult.getCard().getGatewayId(),payment.getGatewayId());
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(),payment.getPaymentAuthId());
        assertEquals(prResponse.getResults().get(0).getGatewayResult().getAmount().get(3).getValue(),
                payment.getAuthorizedAmount());
        assertEquals( TransactionStatus.SUCCESS, payment.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), payment.getGatewayTransactionStatusCode());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), payment.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), payment.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), payment.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), payment.getDeferredAuth());
        assertNotNull(payment.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), payment.getUpdatedBy());
    }

    @Test
    void testUpdateRouterResponseInTheDBRecord_Error() throws IOException {
        PaymentEntity payment= mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        payment.setGroupId("groupId-1");
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        gatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());
        authorizePaymentProcessor.updateRouterResponseInTheDBRecord(payment,prResponse, user, headersDTO, Collections.singletonList(payment));
        verify(repository).save(payment, headersDTO);

        assertEquals(gatewayResult.getTransaction().getGatewayChainId(),payment.getGatewayChainId());
        assertEquals(gatewayResult.getCard().getGatewayId(),payment.getGatewayId());
        assertEquals(gatewayResult.getTransaction().getAuthorizationCode(),payment.getPaymentAuthId());
        assertEquals( new BigDecimal(0),
                payment.getAuthorizedAmount());
        assertEquals( TransactionStatus.FAILURE, payment.getTransactionStatus());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonCode(), payment.getGatewayTransactionStatusCode());
        assertEquals(gatewayResult.getTransaction().getGatewayResponse().getReasonDescription(), payment.getGatewayTransactionStatusReason());
        assertEquals(gatewayResult.getTransaction().getResponseCode(), payment.getGatewayResponseCode());
        assertEquals(gatewayResult.getTransaction().getRetrievalReference(), payment.getGatewayRrn());
        assertEquals(gatewayResult.getTransaction().getDeferredAuth(), payment.getDeferredAuth());
        assertNotNull(payment.getUpdatedTimestamp());
        assertEquals(user.getServiceId(), payment.getUpdatedBy());
    }

    @Test
    void testUpdateRouterResponseInTheDBRecord_Exception() throws IOException {
        PaymentEntity payment= mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        payment.setGroupId("groupId-1");
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        gatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());

        Mockito.doThrow(new RuntimeException()).when(repository)
                .save(payment, headersDTO);
        Assertions.assertThrows(RuntimeException.class, () ->
        authorizePaymentProcessor.updateRouterResponseInTheDBRecord(payment,prResponse, user, headersDTO, Collections.singletonList(payment)));
    }

    @Test
    void testUpdateRouterResponseInTheDBRecordSuccessException() throws IOException {
        PaymentEntity payment= mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        payment.setGroupId("groupId-1");
        PaymentRouterResponse prResponse = mockRequestCreator.createMockRouterResponse();
        RouterGatewayResult gatewayResult = prResponse.getResults().stream().findFirst().get().getGatewayResult();
        gatewayResult.getTransaction().setResponseCode(RouterResponseCode.A.toString());

        Mockito.doThrow(new RuntimeException()).when(repository)
                .save(payment, headersDTO);
        Assertions.assertThrows(RuntimeException.class, () ->
                authorizePaymentProcessor.updateRouterResponseInTheDBRecord(payment,prResponse, user, headersDTO, Collections.singletonList(payment)));
    }


    @Test
    void testMapPaymentRouterResponseToPaymentResponse() throws IOException {
        PaymentEntity payment = mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        PaymentResponse paymentResponse = new PaymentResponse();
        authorizePaymentProcessor.mapPaymentRouterResponseToPaymentResponse(payment,
                mockRequestCreator.createMockAuthorizeRequest(),paymentRouterResponse,paymentResponse, headersDTO);
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        assertEquals(payment.getPaymentId(), gatewayResult.getTransaction().getPaymentId());
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());

    }

    @Test
    void testMapPaymentRouterResponseToPaymentResponse_Error() throws IOException {
        PaymentEntity payment = mockRequestCreator.createAuthPaymentEntity(headersDTO,user);
        PaymentRouterResponse paymentRouterResponse = mockRequestCreator.createMockRouterResponse();
        RouterGatewayResult routerGatewayResult = paymentRouterResponse.getResults().get(0).getGatewayResult();
        routerGatewayResult.getTransaction().setResponseCode(RouterResponseCode.F.toString());
        PaymentResponse paymentResponse = new PaymentResponse();
        authorizePaymentProcessor.mapPaymentRouterResponseToPaymentResponse(payment,
                mockRequestCreator.createMockAuthorizeRequest(),paymentRouterResponse,paymentResponse, headersDTO);
        GatewayResult gatewayResult = paymentResponse.getResults().get(0).getGatewayResult();
        assertEquals(payment.getPaymentId(), gatewayResult.getTransaction().getPaymentId());
        assertEquals(routerGatewayResult.getCard().getGatewayId(), gatewayResult.getCard().getGatewayId());
        assertEquals(routerGatewayResult.getTransaction().getGatewayResponse(), gatewayResult.getTransaction().getGatewayResponse());
        assertEquals(SUCCESS_RESPONSE_STATUS_CODE.toString(), paymentResponse.getStatusCode());
        assertEquals(SUCCESS_RESPONSE_STATUS, paymentResponse.getStatusDesc());

    }

}
