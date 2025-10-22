package com.procurement.email.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * JPA entity representing a purchase order.
 */
@Entity
@Table(name = "purchase_orders")
@Data
public class PurchaseOrder {
    
    @Id
    @Column(name = "po_number", nullable = false, unique = true)
    private String poNumber;
    
    @Column(name = "item_name", nullable = false)
    private String itemName;
    
    @Column(name = "item_type", nullable = false)
    private String itemType;
    
    @ElementCollection
    @CollectionTable(name = "po_specifications", joinColumns = @JoinColumn(name = "po_number"))
    @MapKeyColumn(name = "spec_key")
    @Column(name = "spec_value")
    private Map<String, String> specifications;
    
    @Column(name = "quantity", nullable = false)
    private int quantity;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;
    
    @Column(name = "requester_role")
    private String requesterRole;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "status", nullable = false)
    private String status;
}
