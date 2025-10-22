package com.procurement.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.integration.config.EnableIntegration;

@Slf4j
@SpringBootApplication
@EnableIntegration
public class ProcurementEmailApplication {

    public static void main(String[] args) {
        log.info("Starting Procurement Email Automation System...");
        SpringApplication.run(ProcurementEmailApplication.class, args);
        log.info("Procurement Email Automation System started successfully");
    }

    @Bean
    public CommandLineRunner startupLogger(Environment env) {
        return args -> {
            log.info("=".repeat(80));
            log.info("Procurement Email Automation System - Configuration Verification");
            log.info("=".repeat(80));
            log.info("Active Profiles: {}", String.join(", ", env.getActiveProfiles()));
            log.info("Email Host: {}", env.getProperty("spring.mail.host"));
            log.info("Email Port: {}", env.getProperty("spring.mail.port"));
            log.info("Email Username: {}", env.getProperty("spring.mail.username"));
            String password = env.getProperty("spring.mail.password");
            log.info("Email Password: {}", password != null && !password.isEmpty() ? "***SET***" : "NOT SET");
            log.info("Email Protocol: {}", env.getProperty("spring.mail.protocol"));
            log.info("Keycloak Server: {}", env.getProperty("keycloak.auth-server-url"));
            log.info("Keycloak Realm: {}", env.getProperty("keycloak.realm"));
            log.info("Gemini API URL: {}", env.getProperty("gemini.api.url"));
            String geminiKey = env.getProperty("gemini.api.key");
            log.info("Gemini API Key: {}", geminiKey != null && !geminiKey.isEmpty() ? "***SET***" : "NOT SET");
            log.info("Email Polling Interval: {} ms", env.getProperty("email.polling.interval"));
            log.info("H2 Console Enabled: {}", env.getProperty("spring.h2.console.enabled"));
            log.info("H2 Console Path: {}", env.getProperty("spring.h2.console.path"));
            log.info("=".repeat(80));
            log.info("System is ready to process procurement emails");
            log.info("=".repeat(80));
        };
    }
}
