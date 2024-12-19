package com.mgm.payments.processing.service.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;

@Configuration
@AllArgsConstructor
@Profile("local")
public class WebClientLocalConfig {

    PPSProperties ppsProperties;

    @Bean
    @Primary
    @Profile("local")
    public WebClient webClient() {

        WebClient webClient = null;
        SslContext sslContext;
        try {
            sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            ClientHttpConnector httpConnector = new ReactorClientHttpConnector(
                    HttpClient.create(connectionProvider()).secure(t -> t.sslContext(sslContext))
                            .resolver(DefaultAddressResolverGroup.INSTANCE)
            );
            webClient = WebClient.builder()
                    .clientConnector(httpConnector)
                    .build();
        } catch (SSLException e) {
            e.printStackTrace();
        }

        return webClient;
    }



    private ConnectionProvider connectionProvider() {
        String connectionProviderName = ppsProperties.getConnectionProviderName();
        Integer maxConnections = ppsProperties.getMaxConnections();
        Duration maxIdleTime = Duration.ofMillis(ppsProperties.getMaxIdleTime());
        return ConnectionProvider.builder(connectionProviderName)
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .pendingAcquireMaxCount(100)
                .maxIdleTime(maxIdleTime)
                .metrics(true)
                .build();
    }
}
