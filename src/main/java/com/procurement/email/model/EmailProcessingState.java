package com.procurement.email.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA entity for tracking email processing workflow state.
 */
@Entity
@Table(name = "email_processing_states")
@Data
public class EmailProcessingState {
    
    @Id
    @Column(name = "email_message_id", nullable = false, unique = true)
    private String emailMessageId;
    
    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;
    
    @Column(name = "current_state", nullable = false)
    private String currentState;
    
    @Column(name = "procurement_request_id")
    private String procurementRequestId;
    
    @Column(name = "po_number")
    private String poNumber;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @Column(name = "state_data", columnDefinition = "TEXT")
    private String stateData; // JSON blob for additional context
}
