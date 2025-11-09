package com.procurement.email.repository;

import com.procurement.email.model.EmailThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for email thread storage and retrieval.
 */
@Repository
public interface EmailThreadRepository extends JpaRepository<EmailThread, Long> {
    
    /**
     * Find all emails in a thread by PO number, ordered by received date.
     */
    @Query("SELECT et FROM EmailThread et WHERE et.poNumber = :poNumber ORDER BY et.receivedAt ASC")
    List<EmailThread> findByPoNumberOrderByReceivedAtAsc(@Param("poNumber") String poNumber);
    
    /**
     * Find email by message ID.
     */
    Optional<EmailThread> findByMessageId(String messageId);
    
    /**
     * Find all child emails of a parent message.
     */
    List<EmailThread> findByParentMessageIdOrderByReceivedAtAsc(String parentMessageId);
    
    /**
     * Find root emails (no parent) for a PO number.
     */
    @Query("SELECT et FROM EmailThread et WHERE et.poNumber = :poNumber AND et.parentMessageId IS NULL ORDER BY et.receivedAt ASC")
    List<EmailThread> findRootEmailsByPoNumber(@Param("poNumber") String poNumber);
    
    /**
     * Find all emails from a specific sender.
     */
    List<EmailThread> findByFromEmailOrderByReceivedAtDesc(String fromEmail);


    
    /**
     * Find procurement-related emails only.
     */
    List<EmailThread> findByProcurementRelatedTrueOrderByReceivedAtDesc();
    
    /**
     * Find emails by processing state.
     */
    List<EmailThread> findByProcessingStateOrderByReceivedAtDesc(String processingState);
    
    /**
     * Find recent emails (last N days).
     */
    @Query("SELECT et FROM EmailThread et WHERE et.receivedAt >= :cutoffDate ORDER BY et.receivedAt DESC")
    List<EmailThread> findRecentEmails(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Count emails in a thread.
     */
    @Query("SELECT COUNT(et) FROM EmailThread et WHERE et.poNumber = :poNumber")
    long countByPoNumber(@Param("poNumber") String poNumber);
    
    /**
     * Find all unique PO numbers.
     */
    @Query("SELECT DISTINCT et.poNumber FROM EmailThread et ORDER BY et.poNumber DESC")
    List<String> findAllDistinctPoNumbers();
}
