package com.banquito.switchpagos.batch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CoreKongRestClientConfig {

    @Bean
    public RestClient coreKongRestClient(
            @Value("${core.api-gateway.base-url}") String baseUrl,
            @Value("${core.api-gateway.api-key:}") String apiKey,
            @Value("${core.api-gateway.connect-timeout-ms}") Long connectTimeoutMs,
            @Value("${core.api-gateway.read-timeout-ms}") Long readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("x-api-key", apiKey.trim());
        }
        return builder.build();
    }
}
