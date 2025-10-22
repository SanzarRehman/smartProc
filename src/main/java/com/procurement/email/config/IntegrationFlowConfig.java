package com.procurement.email.config;

import com.procurement.email.model.EmailMessage;
import com.procurement.email.service.EmailProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.interceptor.WireTap;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.router.AbstractMessageRouter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ErrorMessage;

import java.util.Collection;
import java.util.Collections;

/**
 * Configuration for Spring Integration flows that wire together the email processing workflow.
 * Defines service activators, message routers, and error handling for the procurement system.
 */
@Slf4j
@Configuration
@EnableIntegration
@RequiredArgsConstructor
public class IntegrationFlowConfig {

    private final EmailProcessingService emailProcessingService;
    private final MessageChannel incomingEmailChannel;
    private final MessageChannel confirmationChannel;
    private final MessageChannel errorChannel;

    /**
     * Service activator for incoming emails.
     * Routes new emails to EmailProcessingService.processIncomingEmail().
     */
    @Bean
    @ServiceActivator(inputChannel = "incomingEmailChannel")
    public MessageHandler incomingEmailHandler() {
        return message -> {
            try {
                EmailMessage email = (EmailMessage) message.getPayload();
                log.info("Service activator received incoming email from: {}", email.getFrom());
                
                // Route to appropriate handler based on email type
                if (isConfirmationEmail(email)) {
                    log.debug("Email identified as confirmation, routing to confirmation handler");
                    emailProcessingService.processConfirmationEmail(email);
                } else {
                    log.debug("Email identified as new request, routing to incoming handler");
                    emailProcessingService.processIncomingEmail(email);
                }
                
            } catch (Exception e) {
                log.error("Error in incoming email handler", e);
                throw new RuntimeException("Failed to process incoming email", e);
            }
        };
    }

    /**
     * Service activator for confirmation emails.
     * Routes confirmation emails to EmailProcessingService.processConfirmationEmail().
     */
    @Bean
    @ServiceActivator(inputChannel = "confirmationChannel")
    public MessageHandler confirmationEmailHandler() {
        return message -> {
            try {
                EmailMessage email = (EmailMessage) message.getPayload();
                log.info("Service activator received confirmation email from: {}", email.getFrom());
                emailProcessingService.processConfirmationEmail(email);
            } catch (Exception e) {
                log.error("Error in confirmation email handler", e);
                throw new RuntimeException("Failed to process confirmation email", e);
            }
        };
    }

    /**
     * Message router to distinguish between new requests and confirmations.
     * Routes emails to appropriate channels based on subject and content.
     */
    @Bean
    public AbstractMessageRouter emailRouter() {
        return new AbstractMessageRouter() {
            @Override
            protected Collection<MessageChannel> determineTargetChannels(Message<?> message) {
                EmailMessage email = (EmailMessage) message.getPayload();
                
                log.debug("Routing email from: {}, subject: {}", email.getFrom(), email.getSubject());
                
                if (isConfirmationEmail(email)) {
                    log.info("Routing to confirmationChannel");
                    return Collections.singleton(confirmationChannel);
                } else {
                    log.info("Routing to incomingEmailChannel for processing");
                    return Collections.singleton(incomingEmailChannel);
                }
            }
        };
    }

    /**
     * Error channel handler for failed messages.
     * Logs errors and attempts recovery where possible.
     * Does NOT log the full email body to avoid cluttering logs.
     */
    @Bean
    @ServiceActivator(inputChannel = "errorChannel")
    public MessageHandler errorHandler() {
        return message -> {
            if (message instanceof ErrorMessage) {
                ErrorMessage errorMessage = (ErrorMessage) message;
                Throwable throwable = errorMessage.getPayload();
                Message<?> failedMessage = errorMessage.getOriginalMessage();
                
                log.error("Error channel received failed message. Error: {}", throwable.getMessage());
                
                if (failedMessage != null && failedMessage.getPayload() instanceof EmailMessage) {
                    EmailMessage email = (EmailMessage) failedMessage.getPayload();
                    
                    // Log only essential details, not the full body
                    String bodyPreview = email.getBody() != null && email.getBody().length() > 100 
                        ? email.getBody().substring(0, 100) + "..." 
                        : email.getBody();
                    
                    log.error("Failed email - From: {}, Subject: {}, MessageId: {}, Body preview: {}", 
                            email.getFrom(), email.getSubject(), email.getMessageId(), bodyPreview);
                    
                    // Attempt to send error notification to user
                    try {
                        // This would be handled by EmailProcessingService error handling
                        log.info("Error notification would be sent to: {}", email.getFrom());
                    } catch (Exception e) {
                        log.error("Failed to send error notification", e);
                    }
                }
            } else {
                log.error("Error channel received non-ErrorMessage type");
            }
        };
    }

    // ==================== Helper Methods ====================

    /**
     * Determines if an email is a confirmation response.
     * Checks In-Reply-To header first (most reliable), then subject and body.
     * Uses only the first 500 characters of body to avoid processing huge quoted chains.
     */
    private boolean isConfirmationEmail(EmailMessage email) {
        if (email == null) {
            return false;
        }
        
        // Most reliable: Check if this is a reply using email headers
        boolean isReply = email.getInReplyTo() != null && !email.getInReplyTo().isEmpty();
        
        String subject = email.getSubject() != null ? email.getSubject().toLowerCase() : "";
        
        // Only check first 500 chars of body to avoid processing entire quoted chain
        String body = email.getBody() != null ? email.getBody().toLowerCase() : "";
        if (body.length() > 500) {
            body = body.substring(0, 500);
        }
        
        // If it's a reply (has In-Reply-To header), check if it looks like a confirmation
        if (isReply) {
            // Check for confirmation indicators in body
            boolean hasConfirmationIndicators = 
                body.contains("confirm") || 
                body.contains("yes") || 
                body.contains("approve") ||
                body.contains("accept") ||
                body.contains("option") ||  // "Option 1", "Option 2", etc.
                body.contains("lap-") ||  // Item ID patterns
                body.contains("mon-") ||
                body.contains("acc-") ||
                body.contains("macbook") ||
                body.contains("dell") ||
                body.contains("thinkpad");
            
            return hasConfirmationIndicators;
        }
        
        // If not a reply, check subject for explicit confirmation keywords
        boolean subjectMatch = subject.contains("confirmation") || 
                               subject.contains("confirm") ||
                               subject.contains("approve");
        
        return subjectMatch;
    }
}
