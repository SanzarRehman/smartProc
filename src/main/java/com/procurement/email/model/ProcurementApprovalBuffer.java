package com.procurement.email.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Buffer table to store procurement context for resuming after human approval.
 * Stores all necessary information to complete the procurement workflow.
 */
@Entity
@Table(name = "procurement_approval_buffer")
@Data
public class ProcurementApprovalBuffer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String poNumber;
    
    @Column(nullable = false)
    private String requesterEmail;
    
    @Column(nullable = false)
    private String originalMessageId;
    
    @Column(nullable = false)
    private String originalSubject;
    
    @Column(nullable = false)
    private String approvalType; // "inventory" or "procurement"
    
    @Column(nullable = false)
    private String username;
    
    // Serialized ProcurementRequest JSON
    @Column(columnDefinition = "TEXT")
    private String requestData;
    
    // Selected item ID (for inventory allocation)
    private String selectedItemId;
    
    // Quantity
    private Integer quantity;
    
    @Column(nullable = false)
    private String currentState; // "PENDING_APPROVAL", "APPROVED", "REJECTED"
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime approvedAt;
    
    private String approvedBy;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (currentState == null) {
            currentState = "PENDING_APPROVAL";
        }
    }
}
