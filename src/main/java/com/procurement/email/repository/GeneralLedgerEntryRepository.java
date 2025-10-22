package com.procurement.email.repository;

import com.procurement.email.model.GeneralLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA repository for GeneralLedgerEntry entities.
 */
@Repository
public interface GeneralLedgerEntryRepository extends JpaRepository<GeneralLedgerEntry, Long> {
    
    /**
     * Find GL entries by transaction type.
     */
    List<GeneralLedgerEntry> findByTransactionType(String transactionType);
    
    /**
     * Find GL entries by reference number (PO number or allocation ID).
     */
    List<GeneralLedgerEntry> findByReferenceNumber(String referenceNumber);
    
    /**
     * Find GL entries by requester email.
     */
    List<GeneralLedgerEntry> findByRequesterEmail(String requesterEmail);
    
    /**
     * Find GL entries within a date range.
     */
    List<GeneralLedgerEntry> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
}
