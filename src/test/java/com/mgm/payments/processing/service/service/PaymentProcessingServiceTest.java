package com.mgm.payments.processing.service.service;

import com.mgm.payments.processing.service.MockRequestCreator;
import com.mgm.payments.processing.service.external.PaymentRouterServiceCaller;
import com.mgm.payments.processing.service.mapper.AuditMapper;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.User;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import com.mgm.payments.processing.service.model.payload.router.PaymentRouterResponse;
import com.mgm.payments.processing.service.repository.jpa.PaymentProcessingRepositoryWrapper;
import com.mgm.payments.processing.service.service.processor.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {PaymentProcessingServiceTest.class})
@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
class PaymentProcessingServiceTest {

    @InjectMocks
    PaymentProcessingService service;

    @Mock
    PaymentProcessingRepositoryWrapper repository;


    @Mock
    AuthorizePaymentProcessor authorizePaymentProcessor;

    @Mock
    CapturePaymentProcessor capturePaymentProcessor;

    @Mock
    VoidPaymentProcessor voidPaymentProcessor;

    @Mock
    RefundPaymentProcessor refundPaymentProcessor;


    @Mock
    AuditMapper auditMapper;


    @Spy
    List<PaymentProcessor> paymentProcessorList = new ArrayList<>();
    @Mock
    PaymentRouterServiceCaller paymentRouterCaller;


    @Mock
    AuditMapper auditLog;
    PaymentResponse paymentResp;
    PaymentRouterResponse prResponse;
    MockRequestCreator mockRequestCreator = new MockRequestCreator();
    @Captor
    ArgumentCaptor<LocalDateTime> localDateArgumentCaptor;
    private User user;
    private HeadersDTO headersDTO;

    @BeforeEach
    void init(){

        user = new User("00uutm8em5h0EU2eV1t7", null, null, "Mike", "Mike Doe", "mgm_app_web", "email", "mLifeNumber", "jwtToken", "service_id");
        headersDTO = new HeadersDTO("pamService", "01-journeyId", "01-correlationId", "01-transactionId", "WEB", "jwtToken", "clientId", "userAgent");

    }


    @Test
    void testAuthorize() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockAuthorizeRequest();
        when(authorizePaymentProcessor.process(paymentRequest, user, headersDTO)).thenReturn(Mono.just(Mockito.mock(PaymentResponse.class)));
        PaymentResponse paymentResponse = service.authorize(paymentRequest, user, headersDTO).block();
        assertNotNull(paymentResponse);
    }

    @Test
    void testCapture() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockCaptureRequest();
        when(capturePaymentProcessor.
                process(paymentRequest, user, headersDTO)).thenReturn(Mono.just(Mockito.mock(PaymentResponse.class)));
        PaymentResponse paymentResponse = service.capture(paymentRequest, user, headersDTO).block();
        assertNotNull(paymentResponse);
    }




    @Test
    void testRefund() throws IOException {
        PaymentRequest paymentRequest = mockRequestCreator.createMockRefundRequest();
        when(refundPaymentProcessor.
                process(paymentRequest, user, headersDTO)).thenReturn(Mono.just(Mockito.mock(PaymentResponse.class)));
        PaymentResponse paymentResponse = service.refund(paymentRequest, user, headersDTO).block();
        assertNotNull(paymentResponse);

    }




    @Test
    void testVoid(){
        PaymentRequest paymentRequest = mockRequestCreator.createMockVoidRequest();
        when(voidPaymentProcessor.
                process(paymentRequest, user, headersDTO)).thenReturn(Mono.just(Mockito.mock(PaymentResponse.class)));
        PaymentResponse paymentResponse = service.voidCall(paymentRequest, user, headersDTO).block();
        assertNotNull(paymentResponse);
    }


}
