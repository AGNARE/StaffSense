package com.staffsense.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP-клиент для вызовов StaffSenseML.
 *
 * <p>NFR-003: таймаут connect + read = 2,0 с. Ранее использовался
 * {@code new RestTemplate()} без таймаутов, из-за чего зависший ML-сервис
 * блокировал рабочие потоки Tomcat бесконечно (BR-006 требовал fallback
 * уже после 2,0 с).
 *
 * <p>Значения настраиваются через ML_SERVICE_CONNECT_TIMEOUT_MS и
 * ML_SERVICE_READ_TIMEOUT_MS.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder,
                                     @Value("${ml.service.connect-timeout-ms:2000}") long connectTimeoutMs,
                                     @Value("${ml.service.read-timeout-ms:2000}") long readTimeoutMs) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
