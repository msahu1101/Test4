package com.mgm.payments.processing.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "pps")
@Data
@Configuration
public class PPSProperties {
    private final Logger logger = LoggerFactory.getLogger(PPSProperties.class);

    private int webClientTimeout;
    private String auditTrailSecrets;
    private String paymentRouterUrl;
    private String pamUrl;
    private String clientConfigurationUrl;
    private String serviceTokenHost;
    private String serviceTokenUri;
    private String serviceTokenClientId;
    private String serviceTokenClientSecret;
    private String serviceTokenGrantType;
    private String serviceTokenScope;
    //Connection Pool Properties
    private String connectionProviderName;
    private long maxIdleTime;
    private Integer maxConnections;
    private long pendingAcquireTimeout;
    private long duplicateValidationDuration;
    private long retryCount;
    private long retryDelay;
    private String ppsConfigKeys;
    private String sessionUrl;
    private Boolean readFromCache;

    public PPSConfig getPPSConfig(){
        try {
            return new ObjectMapper().readValue(getPpsConfigKeys(), PPSConfig.class);
        } catch (JsonProcessingException e) {
            logger.error("Exception while extracting PPS Config", e);
        }
        return null;
    }
}