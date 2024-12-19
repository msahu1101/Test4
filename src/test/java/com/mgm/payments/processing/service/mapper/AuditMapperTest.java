package com.mgm.payments.processing.service.mapper;

import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.constants.PaymentProcessingConstants;
import com.mgm.payments.processing.service.model.AuditData;
import com.mgm.payments.processing.service.model.CustomAuditEvent;
import com.mgm.payments.processing.service.model.HeadersDTO;
import com.mgm.payments.processing.service.model.payload.pps.PaymentRequest;
import com.mgm.payments.processing.service.model.payload.pps.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.mgm.payments.processing.service.constants.PaymentProcessingConstants.SUCCESS_RESPONSE_STATUS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AuditMapperTest.class)
class AuditMapperTest {

    @InjectMocks
    AuditMapper auditMapper;

    @Mock
    private ApplicationEventPublisher publisher;

    @Mock
    private Clock clock ;

    private HeadersDTO headersDTO;

    @BeforeEach
    void init(){
        headersDTO = new HeadersDTO("web", "01-journeyId", "01-correlationId", "01-transactionId", "WEB", "jwtToken", "clientId", "userAgent");
    }

    @Test
    void testCreateAndPublishAuditTrailForRequestResponse() {
        when(clock.instant()).thenReturn(Clock.systemDefaultZone().instant());
        when(clock.getZone()).thenReturn(Clock.systemDefaultZone().getZone());
        AuditData auditData = AuditData.builder()
                .eventName("Void")
                .eventDescription("Void the given Request")
                .status(SUCCESS_RESPONSE_STATUS)
                .startTimeTS(LocalDateTime.now())
                .endTimeTS(LocalDateTime.now())
                .serviceName(AuditTrailConstants.SERVICE_NAME)
                .mgmErrorCode(null)
                .externalErrorCode(null)
                .errorDescription(null)
                .subject(PaymentProcessingConstants.PPS_AUDIT_SUBJECT)
                .requestPayload( Mockito.mock(PaymentRequest.class))
                .responsePayload(Mockito.mock(PaymentResponse.class))
                .gateWayId("SHFT")
                .lastFour("1234")
                .mgmToken("3245321234")
                .build();
        auditMapper.createAndPublishAuditTrailForRequestResponse( "clientReferenceNumber",
                "gatewayRRN","sessionId",
                "mgmId", "executionId",
                headersDTO, auditData);
        Mockito.verify(publisher).publishEvent(Mockito.any(CustomAuditEvent.class));

    }

    @Test
    void testCreateAndPublishAuditTrailException() {
        when(clock.instant()).thenReturn(Clock.systemDefaultZone().instant());
        when(clock.getZone()).thenReturn(Clock.systemDefaultZone().getZone());
        AuditData auditData = AuditData.builder()
                .eventName("Void")
                .eventDescription("Void the given Request")
                .status(SUCCESS_RESPONSE_STATUS)
                .startTimeTS(LocalDateTime.now())
                .endTimeTS(LocalDateTime.now())
                .serviceName(AuditTrailConstants.SERVICE_NAME)
                .mgmErrorCode(null)
                .externalErrorCode(null)
                .errorDescription(null)
                .subject(PaymentProcessingConstants.PPS_AUDIT_SUBJECT)
                .requestPayload( Mockito.mock(PaymentRequest.class))
                .responsePayload(Mockito.mock(PaymentResponse.class))
                .gateWayId("SHFT")
                .lastFour("1234")
                .mgmToken("3245321234")
                .build();
        assertThrows(Exception.class, () -> auditMapper.createAndPublishAuditTrailForRequestResponse( "clientReferenceNumber",
                "gatewayRRN","sessionId",
                "mgmId", "executionId",
                null, auditData));
    }
   
}
