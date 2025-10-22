package com.procurement.email.repository;

import com.procurement.email.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for PurchaseOrder entities.
 */
@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
    
    /**
     * Find purchase orders by requester email.
     */
    List<PurchaseOrder> findByRequesterEmail(String requesterEmail);
    
    /**
     * Find purchase orders by status.
     */
    List<PurchaseOrder> findByStatus(String status);
    
    /**
     * Find purchase orders created after a specific date.
     */
    List<PurchaseOrder> findByCreatedAtAfter(LocalDateTime date);
    
    /**
     * Find a purchase order by PO number.
     */
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
