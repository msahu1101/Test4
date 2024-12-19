package com.mgm.payments.processing.service.mapper;

import com.mgm.payments.processing.service.model.AuditData;
import com.mgm.payments.processing.service.model.AuditRequest;
import com.mgm.payments.processing.service.model.CustomAuditEvent;
import com.mgm.payments.processing.service.model.HeadersDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class AuditMapper {
    private final Logger logger = LoggerFactory.getLogger(AuditMapper.class);

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Autowired
    public AuditMapper(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    public void createAndPublishAuditTrailForRequestResponse( String clientReferenceNumber,
                                                             String gatewayChainId,
                                                             String sessionId, String mgmId, String executionId, HeadersDTO headersDTO,
                                                            AuditData auditData) {

        try {
            auditData.setCreatedDate(LocalDateTime.now(clock));
            AuditRequest auditRequest = AuditRequest.builder()
                    .eventType("mgm.payments.audittrail")
                    .eventTime(LocalDateTime.now().toString())
                    .clientReferenceNumber(clientReferenceNumber)
                    .gatewayChainId(gatewayChainId)
                    .subject(auditData.getSubject())
                    .sessionId(sessionId)
                    .mgmId(mgmId)
                    .clientId(headersDTO.getClientId())
                    .journeyId(headersDTO.getMgmJourneyId())
                    .executionId(executionId)
                    .correlationId(headersDTO.getMgmCorrelationId())
                    .transactionId(headersDTO.getMgmTransactionId())
                    .mgmChannel(headersDTO.getMgmChannel())
                    .createdDate(LocalDateTime.now(clock))
                    .cardEntryMode("CNP")
                    .auditData(auditData).build();
            CustomAuditEvent event = new CustomAuditEvent(this, auditRequest);
            publisher.publishEvent(event);
            logger.debug("End : AuditMapper createAndPublishAuditTrail method eventName :{}", auditData.getEventName());
        } catch (Exception e) {
            logger.error("Exception in createAndPublishAuditTrail method mgmId: {} ,eventName :{}, request: {}", mgmId,
                    auditData.getEventName(), auditData.getRequestPayload());
            throw e;
        }
    }

}
