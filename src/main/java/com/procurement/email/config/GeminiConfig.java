package com.procurement.email.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for Gemini API integration.
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.timeout:10000}")
    private int timeout;

    @Bean
    public RestTemplate geminiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .additionalInterceptors(geminiApiKeyInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor geminiApiKeyInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().add("x-goog-api-key", apiKey);
            request.getHeaders().add("Content-Type", "application/json");
            return execution.execute(request, body);
        };
    }
}
