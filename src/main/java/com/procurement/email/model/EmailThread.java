package com.procurement.email.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA entity for storing email threads with nested chain structure.
 * Tracks entire email conversation history for UI display.
 */
@Entity
@Table(name = "email_threads")
@Data
public class EmailThread {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * PO number linking all emails in this procurement thread.
     * Generated at the start when first email arrives.
     */
    @Column(name = "po_number", nullable = false)
    private String poNumber;
    
    /**
     * Original email message ID (from email headers).
     */
    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;
    
    /**
     * Parent message ID for threading (In-Reply-To header).
     */
    @Column(name = "parent_message_id")
    private String parentMessageId;
    
    /**
     * Email sender address.
     */
    @Column(name = "from_email", nullable = false)
    private String fromEmail;
    
    /**
     * Email recipient(s).
     */
    @Column(name = "to_email", nullable = false)
    private String toEmail;
    
    /**
     * Email subject line.
     */
    @Column(name = "subject", length = 500)
    private String subject;
    
    /**
     * Cleaned email body (stripped of HTML, signatures, quoted text).
     */
    @Column(name = "cleaned_body", columnDefinition = "TEXT")
    private String cleanedBody;
    
    /**
     * Full raw email content (original with all HTML, headers, etc.).
     */
    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;
    
    /**
     * Whether this email is from the system (auto-generated).
     */
    @Column(name = "is_system_message")
    private boolean systemMessage;
    
    /**
     * Whether this is a procurement-related email.
     */
    @Column(name = "is_procurement_related")
    private boolean procurementRelated;
    
    /**
     * Processing state of this email.
     */
    @Column(name = "processing_state")
    private String processingState;
    
    /**
     * When this email was received.
     */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
    
    /**
     * When this record was created in the database.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * Thread depth level (0 = root, 1 = first reply, etc.).
     */
    @Column(name = "depth_level")
    private int depthLevel;
    
    /**
     * Additional metadata as JSON (for flexibility).
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
