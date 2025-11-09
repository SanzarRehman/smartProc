package com.procurement.email.repository;

import com.procurement.email.model.RequestForRequisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Request for Requisition documents.
 */
@Repository
public interface RequestForRequisitionRepository extends JpaRepository<RequestForRequisition, Long> {
    
    /**
     * Find RRF by PO number.
     */
    Optional<RequestForRequisition> findByPoNumber(String poNumber);
    
    /**
     * Check if RRF exists for a PO number.
     */
    boolean existsByPoNumber(String poNumber);
}
