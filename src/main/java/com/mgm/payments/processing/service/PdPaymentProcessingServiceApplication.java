package com.mgm.payments.processing.service;

import com.microsoft.applicationinsights.attach.ApplicationInsights;
import com.microsoft.applicationinsights.connectionstring.ConnectionString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;

@SpringBootApplication
@EnableRetry
@EnableAsync
@EnableRedisRepositories(basePackages = {"com.mgm.payments.processing.service.repository.redis"})
@Slf4j
public class PdPaymentProcessingServiceApplication implements BeanPostProcessor {
    public static void main(String[] args) {
        ApplicationInsights.attach();
        ConnectionString.configure(System.getenv("AppinsightConnectionString"));
        SpringApplication.run(PdPaymentProcessingServiceApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }


}
