package com.procurement.email.repository;

import com.procurement.email.model.EmailProcessingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for EmailProcessingState entities.
 */
@Repository
public interface EmailProcessingStateRepository extends JpaRepository<EmailProcessingState, String> {
    
    /**
     * Find processing state by email message ID.
     */
    Optional<EmailProcessingState> findByEmailMessageId(String emailMessageId);
    
    /**
     * Find processing states by requester email.
     */
    List<EmailProcessingState> findByRequesterEmail(String requesterEmail);
    
    /**
     * Find processing states by current state.
     */
    List<EmailProcessingState> findByCurrentState(String currentState);
    
    /**
     * Find processing states by PO number.
     */
    Optional<EmailProcessingState> findByPoNumber(String poNumber);
    
    /**
     * Find processing states updated after a specific date.
     */
    List<EmailProcessingState> findByLastUpdatedAfter(LocalDateTime date);
}
