package com.procurement.email.config;

import com.procurement.email.model.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * Configuration for Spring Integration message channels.
 * Defines channels for routing emails through the procurement workflow.
 */
@Slf4j
@Configuration
public class MessageChannelConfig {

    /**
     * Channel for incoming emails from the mail receiver.
     * This is the entry point for all new emails.
     */
    @Bean
    public MessageChannel incomingEmailChannel() {
        DirectChannel channel = new DirectChannel();
        channel.addInterceptor(incomingEmailLoggingInterceptor());
        log.info("Created incomingEmailChannel for new emails");
        return channel;
    }

    /**
     * Channel for emails that need AI classification.
     * Emails are routed here for Gemini API processing.
     */
    @Bean
    public MessageChannel classificationChannel() {
        DirectChannel channel = new DirectChannel();
        log.info("Created classificationChannel for AI processing");
        return channel;
    }

    /**
     * Channel for procurement-related emails.
     * Emails classified as procurement requests are routed here.
     */
    @Bean
    public MessageChannel procurementChannel() {
        DirectChannel channel = new DirectChannel();
        log.info("Created procurementChannel for procurement flow");
        return channel;
    }

    /**
     * Channel for confirmation emails from requesters.
     * Handles user responses confirming item selection.
     */
    @Bean
    public MessageChannel confirmationChannel() {
        DirectChannel channel = new DirectChannel();
        channel.addInterceptor(confirmationLoggingInterceptor());
        log.info("Created confirmationChannel for confirmation emails");
        return channel;
    }

    /**
     * Channel for error handling.
     * Failed messages are routed here for error processing.
     */
    @Bean
    public MessageChannel errorChannel() {
        PublishSubscribeChannel channel = new PublishSubscribeChannel();
        channel.addInterceptor(errorLoggingInterceptor());
        log.info("Created errorChannel for error handling");
        return channel;
    }

    /**
     * Logging interceptor for incoming email channel.
     * Provides detailed logging for debugging the email flow.
     */
    @Bean
    public ChannelInterceptor incomingEmailLoggingInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (message.getPayload() instanceof EmailMessage) {
                    EmailMessage email = (EmailMessage) message.getPayload();
                    log.debug("INTERCEPTOR [incomingEmailChannel] - Email from: {}, Subject: {}, MessageId: {}", 
                            email.getFrom(), email.getSubject(), email.getMessageId());
                }
                return message;
            }

            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                if (ex != null) {
                    log.error("INTERCEPTOR [incomingEmailChannel] - Send failed", ex);
                } else {
                    log.debug("INTERCEPTOR [incomingEmailChannel] - Send completed successfully");
                }
            }
        };
    }

    /**
     * Logging interceptor for confirmation channel.
     * Provides detailed logging for debugging confirmation flow.
     */
    @Bean
    public ChannelInterceptor confirmationLoggingInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                if (message.getPayload() instanceof EmailMessage) {
                    EmailMessage email = (EmailMessage) message.getPayload();
                    log.debug("INTERCEPTOR [confirmationChannel] - Confirmation from: {}, Subject: {}", 
                            email.getFrom(), email.getSubject());
                }
                return message;
            }

            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                if (ex != null) {
                    log.error("INTERCEPTOR [confirmationChannel] - Send failed", ex);
                } else {
                    log.debug("INTERCEPTOR [confirmationChannel] - Send completed successfully");
                }
            }
        };
    }

    /**
     * Logging interceptor for error channel.
     * Provides detailed logging for error handling.
     */
    @Bean
    public ChannelInterceptor errorLoggingInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                log.error("INTERCEPTOR [errorChannel] - Error message received");
                return message;
            }
        };
    }
}
