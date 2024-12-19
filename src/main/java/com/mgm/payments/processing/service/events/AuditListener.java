package com.mgm.payments.processing.service.events;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridPublisherAsyncClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import com.mgm.payments.processing.service.config.PPSProperties;
import com.mgm.payments.processing.service.constants.AuditTrailConstants;
import com.mgm.payments.processing.service.model.AuditRequest;
import com.mgm.payments.processing.service.model.CustomAuditEvent;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class AuditListener implements ApplicationListener<CustomAuditEvent> {
    private final Logger logger = LoggerFactory.getLogger(AuditListener.class);

    private final Clock clock;
    private final PPSProperties ppsProperties;

    @Autowired
    public AuditListener(Clock clock, PPSProperties ppsProperties) {
        this.clock = clock;
        this.ppsProperties = ppsProperties;
    }

    /**
     * this listener listen when application publisher publish customauditevent
     *
     * @param auditEvent - CustomAuditEvent
     */
    @Override
    @Async
    public void onApplicationEvent(CustomAuditEvent auditEvent) {
        try {
            EventGridPublisherAsyncClient<BinaryData> customEventClient = getEventGridPublisherClient();
            AuditRequest auditRequest = auditEvent.getAuditRequest();
            auditRequest.setId(UUID.randomUUID().toString());
            auditRequest.setEventTime(LocalDateTime.now(clock).toString());
            auditRequest.setSubject(AuditTrailConstants.SUBJECT);
            auditRequest.setTopic(AuditTrailConstants.TOPIC);
            auditRequest.setEventType(AuditTrailConstants.EVENT_TYPE);
            auditRequest.setDataVersion(AuditTrailConstants.DATA_VERSION);
            List<BinaryData> events = new ArrayList<>();
            events.add(BinaryData.fromObject(auditRequest));
            Mono.from(customEventClient.sendEvents(events)).subscribe(message ->
                    logger.debug("event sent : {}", message)
            );

        } catch (Exception e) {
            logger.error("Events: AuditListener onApplicationEvent Catch block :: exception : {}, cause:{}, exceptionMessage:{}", e, e.getCause(), e.getMessage());
        }
    }


    /**
     * Method fetches EventGridPublisher Client details
     *
     * @return EventGridPublisherClient
     */
    public EventGridPublisherAsyncClient<BinaryData> getEventGridPublisherClient() {
        Map<String, String> map = getTopicKeyFromVault();
        return new EventGridPublisherClientBuilder().endpoint(map.get(AuditTrailConstants.TOPIC_ENDPOINT))
                .credential(new AzureKeyCredential(map.get(AuditTrailConstants.TOPIC_KEY))).buildCustomEventPublisherAsyncClient();
    }

    /**
     * Fetches audit details from key vault
     *
     * @return Map<String, String> - key-value of audit client details
     */
    public Map<String, String> getTopicKeyFromVault() {
        Map<String, String> map = new HashMap<>();
        String keyVaultSecrets = ppsProperties.getAuditTrailSecrets();
        JSONObject jsonSecret = new JSONObject(keyVaultSecrets);
        String topicEndpoint = jsonSecret.getString(AuditTrailConstants.TOPIC_ENDPOINT);
        String topicKey = jsonSecret.getString(AuditTrailConstants.TOPIC_KEY_NAME);
        map.put(AuditTrailConstants.TOPIC_ENDPOINT, topicEndpoint);
        map.put(AuditTrailConstants.TOPIC_KEY, topicKey);
        return map;

    }


}
