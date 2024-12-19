package com.mgm.payments.processing.service.config;

import lombok.Data;

@Data
public class PPSConfig {
    private String pdServiceId;
    private String pdServicePassword;
    private String redisSecret;
}
