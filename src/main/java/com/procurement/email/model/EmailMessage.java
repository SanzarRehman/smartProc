package com.procurement.email.model;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * POJO representing an email message received by the system.
 */
@Data
public class EmailMessage {
    private String messageId;
    private String from;
    private String subject;
    private String body;
    private LocalDateTime receivedDate;
    private boolean processed;
    private String inReplyTo;  // For tracking email threads
    private String references;  // For tracking email threads
}
