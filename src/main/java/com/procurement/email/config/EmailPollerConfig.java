package com.procurement.email.config;

import com.procurement.email.model.EmailMessage;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.mail.ImapIdleChannelAdapter;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Properties;

/**
 * Configuration for Spring Integration email polling.
 * Sets up IMAP mail receiver using Spring Boot's mail properties.
 */
@Slf4j
@Configuration
public class EmailPollerConfig {

    @Value("${spring.mail.host}")
    private String mailHost;

    @Value("${spring.mail.port}")
    private int mailPort;

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Value("${spring.mail.password}")
    private String mailPassword;

    @Value("${spring.mail.protocol:imaps}")
    private String mailProtocol;

    @Value("${email.polling.interval:60000}")
    private long pollingInterval;

    @Value("${email.polling.max-messages-per-poll:10}")
    private int maxMessagesPerPoll;

    /**
     * Configure Java Mail Session with authentication.
     * This is the official Spring Boot way to configure mail with credentials.
     */
    @Bean
    public Session javaMailSession() {
        log.info("========== CREATING JAVA MAIL SESSION ==========");
        log.info("Mail Host: {}", mailHost);
        log.info("Mail Port: {}", mailPort);
        log.info("Mail Username: {}", mailUsername);
        log.info("Mail Password: {}", mailPassword != null && !mailPassword.isEmpty() ? "***SET***" : "NOT SET");
        log.info("Mail Protocol: {}", mailProtocol);
        
        Properties props = new Properties();
        
        // Set the mail store protocol
        props.setProperty("mail.store.protocol", mailProtocol);
        
        // IMAP specific properties
        props.setProperty("mail.imap.host", mailHost);
        props.setProperty("mail.imap.port", String.valueOf(mailPort));
        props.setProperty("mail.imap.ssl.enable", "true");
        props.setProperty("mail.imap.auth", "true");
        props.setProperty("mail.imap.timeout", "10000");
        props.setProperty("mail.imap.connectiontimeout", "10000");
        
        // IMAPS specific properties (for SSL)
        props.setProperty("mail.imaps.host", mailHost);
        props.setProperty("mail.imaps.port", String.valueOf(mailPort));
        props.setProperty("mail.imaps.ssl.enable", "true");
        props.setProperty("mail.imaps.auth", "true");
        props.setProperty("mail.imaps.timeout", "10000");
        props.setProperty("mail.imaps.connectiontimeout", "10000");
        
        // Debug
        props.setProperty("mail.debug", "false");
        
        // Create session with authenticator
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                log.info("Authenticator called - providing credentials for user: {}", mailUsername);
                return new PasswordAuthentication(mailUsername, mailPassword);
            }
        });
        
        log.info("Java Mail Session created successfully with protocol: {}", mailProtocol);
        return session;
    }

    /**
     * Configure JavaMailSender for sending emails (SMTP).
     * This is needed by EmailSenderService.
     */
    @Bean
    public JavaMailSenderImpl javaMailSender() {
        log.info("========== CREATING JAVA MAIL SENDER (SMTP) ==========");
        
        // Use smtp.gmail.com for sending, not imap.gmail.com
        String smtpHost = mailHost.replace("imap.", "smtp.");
        
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpHost);
        mailSender.setPort(587);  // SMTP port for TLS
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(mailPassword);
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.debug", "false");
        
        log.info("JavaMailSender configured for SMTP on {}:587", smtpHost);
        return mailSender;
    }

    /**
     * Configure IMAP mail receiver using the Java Mail Session.
     * This approach uses Spring Boot's official mail configuration.
     */

    @Bean
    public ImapMailReceiver imapMailReceiver(Session javaMailSession) {
        String url = String.format("%s://%s/INBOX", mailProtocol, mailHost);
        ImapMailReceiver receiver = new ImapMailReceiver(url);
        receiver.setSession(javaMailSession);
        receiver.setShouldMarkMessagesAsRead(true);
        receiver.setShouldDeleteMessages(false);
        receiver.setSimpleContent(true);
        receiver.setAutoCloseFolder(true); // Keep folder open for IDLE
        return receiver;
    }

    /**
     * Message source that polls the mail receiver and converts messages to EmailMessage POJOs.
     */
    @Bean
    @InboundChannelAdapter(channel = "incomingEmailChannel", 
                          poller = @Poller(fixedDelay = "1000"))
    public MessageSource<EmailMessage> mailMessageSource(ImapMailReceiver imapMailReceiver) {
        return () -> {
            try {
                log.debug("Polling for new emails...");
                Object[] messages = imapMailReceiver.receive();
                
                if (messages != null && messages.length > 0) {
                    log.info("Received {} new email(s)", messages.length);
                    
                    // Process the first message and return it
                    // Spring Integration will call this repeatedly to process all messages
                    try {
                        Object firstMessage = messages[0];
                        MimeMessage mimeMessage;
                        
                        // Handle both MimeMessage and Spring Message types
                        if (firstMessage instanceof MimeMessage) {
                            mimeMessage = (MimeMessage) firstMessage;
                        } else if (firstMessage instanceof org.springframework.messaging.Message) {
                            org.springframework.messaging.Message<?> springMessage = 
                                (org.springframework.messaging.Message<?>) firstMessage;
                            mimeMessage = (MimeMessage) springMessage.getPayload();
                        } else {
                            log.error("Unexpected message type: {}", firstMessage.getClass());
                            return null;
                        }
                        
                        EmailMessage emailMessage = convertToEmailMessage(mimeMessage);
                        
                        log.info("Converted email from {} with subject: {}", 
                                emailMessage.getFrom(), emailMessage.getSubject());
                        
                        return org.springframework.messaging.support.MessageBuilder
                                .withPayload(emailMessage)
                                .setHeader("messageId", emailMessage.getMessageId())
                                .build();
                    } catch (jakarta.mail.FolderClosedException e) {
                        log.warn("Folder closed while processing message, will retry on next poll");
                        return null;
                    } catch (ClassCastException e) {
                        log.error("Failed to cast message: {}", e.getMessage());
                        return null;
                    }
                }
                
                log.debug("No new emails found");
                return null;
                
            } catch (Exception e) {
                log.error("Error polling emails: {}", e.getMessage(), e);
                // Return null to continue polling despite errors
                return null;
            }
        };
    }

    /**
     * Convert Jakarta Mail MimeMessage to EmailMessage POJO.
     * Extract content eagerly before folder closes.
     */
    private EmailMessage convertToEmailMessage(MimeMessage mimeMessage) throws Exception {
        EmailMessage emailMessage = new EmailMessage();
        
        // Extract message ID
        String messageId = mimeMessage.getMessageID();
        if (messageId == null) {
            messageId = "msg-" + System.currentTimeMillis();
            log.warn("Email has no Message-ID, generated: {}", messageId);
        } else {
            log.info("Email Message-ID: {}", messageId);
        }
        emailMessage.setMessageId(messageId);
        
        // Extract thread information
        try {
            String[] inReplyToHeaders = mimeMessage.getHeader("In-Reply-To");
            if (inReplyToHeaders != null && inReplyToHeaders.length > 0) {
                emailMessage.setInReplyTo(inReplyToHeaders[0]);
                log.info("Email is a reply to: {}", inReplyToHeaders[0]);
            }
            
            String[] referencesHeaders = mimeMessage.getHeader("References");
            if (referencesHeaders != null && referencesHeaders.length > 0) {
                emailMessage.setReferences(referencesHeaders[0]);
                log.info("Email references: {}", referencesHeaders[0]);
            }
        } catch (Exception e) {
            log.warn("Could not extract thread headers: {}", e.getMessage());
        }
        
        // Extract sender
        if (mimeMessage.getFrom() != null && mimeMessage.getFrom().length > 0) {
            emailMessage.setFrom(mimeMessage.getFrom()[0].toString());
        }
        
        // Extract subject
        emailMessage.setSubject(mimeMessage.getSubject());
        
        // Extract body content - do this BEFORE folder closes
        String bodyContent = "";
        try {
            Object content = mimeMessage.getContent();
            if (content instanceof String) {
                bodyContent = (String) content;
            } else if (content instanceof jakarta.mail.Multipart) {
                bodyContent = extractTextFromMultipart((jakarta.mail.Multipart) content);
            } else {
                bodyContent = content != null ? content.toString() : "";
            }
        } catch (jakarta.mail.FolderClosedException e) {
            log.warn("Folder closed while extracting content, using subject as body");
            bodyContent = "Email subject: " + mimeMessage.getSubject();
        } catch (Exception e) {
            log.error("Error extracting email content: {}", e.getMessage());
            bodyContent = "Error extracting content: " + e.getMessage();
        }
        
        emailMessage.setBody(bodyContent);
        
        // Extract received date
        if (mimeMessage.getReceivedDate() != null) {
            emailMessage.setReceivedDate(
                    LocalDateTime.ofInstant(mimeMessage.getReceivedDate().toInstant(), 
                            ZoneId.systemDefault()));
        } else {
            emailMessage.setReceivedDate(LocalDateTime.now());
        }
        
        emailMessage.setProcessed(false);
        
        return emailMessage;
    }

    /**
     * Extract text content from multipart email messages.
     */
    private String extractTextFromMultipart(jakarta.mail.Multipart multipart) throws Exception {
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < multipart.getCount(); i++) {
            jakarta.mail.BodyPart bodyPart = multipart.getBodyPart(i);
            
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent().toString());
            } else if (bodyPart.isMimeType("text/html")) {
                // For HTML, we'll just use it as-is for now
                result.append(bodyPart.getContent().toString());
            } else if (bodyPart.getContent() instanceof jakarta.mail.Multipart) {
                result.append(extractTextFromMultipart((jakarta.mail.Multipart) bodyPart.getContent()));
            }
        }
        
        return result.toString();
    }

    // Message channels will be defined in a separate configuration
}
