package com.procurement.email.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a general ledger entry for accounting transactions.
 */
@Entity
@Table(name = "general_ledger_entries")
@Data
public class GeneralLedgerEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "transaction_type", nullable = false)
    private String transactionType; // ALLOCATION or PURCHASE
    
    @Column(name = "account_debit", nullable = false)
    private String accountDebit;
    
    @Column(name = "account_credit", nullable = false)
    private String accountCredit;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "reference_number", nullable = false)
    private String referenceNumber; // PO number or allocation ID
    
    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
