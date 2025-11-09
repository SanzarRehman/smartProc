package com.procurement.email.repository;

import com.procurement.email.model.ProcurementApprovalBuffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ProcurementApprovalBuffer entity.
 */
@Repository
public interface ProcurementApprovalBufferRepository extends JpaRepository<ProcurementApprovalBuffer, Long> {
    
    /**
     * Find buffer entry by PO number.
     */
    Optional<ProcurementApprovalBuffer> findByPoNumber(String poNumber);
    
    /**
     * Find buffer entry by PO number and state.
     */
    Optional<ProcurementApprovalBuffer> findByPoNumberAndCurrentState(String poNumber, String currentState);
}
