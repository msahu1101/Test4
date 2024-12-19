package com.mgm.payments.processing.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    private final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        logger.debug("Inside Security Filter chain");
        http = http.cors().and().csrf().disable();
        http.authorizeHttpRequests().antMatchers("/").permitAll();
        http.headers().frameOptions().disable();
        return http.build();
    }
}
