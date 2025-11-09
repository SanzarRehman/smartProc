package com.procurement.email.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for OpenRouter AI API integration.
 */
@Configuration
public class OpenRouterConfig {

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.timeout:30000}")
    private int timeout;

    @Bean
    public RestTemplate openRouterRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .additionalInterceptors(openRouterApiKeyInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor openRouterApiKeyInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + apiKey);
            request.getHeaders().add("Content-Type", "application/json");
            request.getHeaders().add("HTTP-Referer", "https://procurement-automation.com");
            request.getHeaders().add("X-Title", "Procurement Email Automation");
            return execution.execute(request, body);
        };
    }
}
