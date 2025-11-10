package com.procurement.email.service;

import com.procurement.email.model.Item;
import com.procurement.email.model.ProcurementRequest;
import com.procurement.email.model.PurchaseOrder;
import com.procurement.email.model.UserContext;
import com.procurement.email.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Service responsible for generating purchase orders for procurement requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementAgentService {
    
    private final PurchaseOrderRepository purchaseOrderRepository;
    
    /**
     * Generates a purchase order for a procurement request.
     * 
     * @param request the procurement request containing item details
     * @param requester the user context of the requester
     * @param selectedItem the item selected for purchase (can be null for new purchases)
     * @return the created PurchaseOrder
     */
    @Transactional
    public PurchaseOrder generatePurchaseOrder(ProcurementRequest request, UserContext requester, Item selectedItem,String poNumber) {
        log.info("Generating purchase order for requester: {}, item type: {}", 
                requester.getEmail(), request.getItemType());
        
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        
        // Generate unique PO number using timestamp and UUID
        purchaseOrder.setPoNumber(generatePoNumber());
        
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        purchaseOrder.setQuantity(quantity);
        
        // Populate item details
        if (selectedItem != null) {
            // Using item from inventory - use actual inventory data
            purchaseOrder.setItemName(selectedItem.getName());
            purchaseOrder.setItemType(selectedItem.getType());
            purchaseOrder.setSpecifications(selectedItem.getSpecifications());
            purchaseOrder.setAmount(selectedItem.getBookValue().multiply(BigDecimal.valueOf(quantity)));
            
            log.info("Inventory allocation: itemName={}, itemId={}, unitPrice={}, quantity={}, totalAmount={}", 
                    selectedItem.getName(), selectedItem.getId(), selectedItem.getBookValue(), 
                    quantity, purchaseOrder.getAmount());
        } else {
            // For new purchases - use details from the request
            // In production, this would come from vendor quotes
            String itemName = (request.getItemName() != null && !request.getItemName().isEmpty()) 
                ? request.getItemName() 
                : request.getItemType();
            
            purchaseOrder.setItemName(itemName);
            purchaseOrder.setItemType(request.getItemType());
            purchaseOrder.setSpecifications(request.getSpecifications());
            
            // Use AI-estimated price if available, otherwise use a placeholder
            // In production, this would be replaced with actual vendor quotes
            BigDecimal unitPrice = (request.getEstimatedPrice() != null && request.getEstimatedPrice().compareTo(BigDecimal.ZERO) > 0)
                ? request.getEstimatedPrice()
                : BigDecimal.valueOf(0.00);  // Placeholder - will be updated with vendor quote
            
            purchaseOrder.setAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            
            log.info("New purchase order: itemName={}, unitPrice={} (pending vendor quote), quantity={}, totalAmount={}", 
                    itemName, unitPrice, quantity, purchaseOrder.getAmount());
        }
        
        // Populate requester information
        purchaseOrder.setRequesterEmail(requester.getEmail());
        purchaseOrder.setRequesterRole(requester.getRole());
        
        // Set timestamps and status
        purchaseOrder.setCreatedAt(LocalDateTime.now());
        purchaseOrder.setStatus("PENDING");
        purchaseOrder.setPoNumber(poNumber);
        
        // Save to database
        PurchaseOrder savedPo = purchaseOrderRepository.save(purchaseOrder);
        
        log.info("Purchase order created successfully: PO Number: {}, Amount: {}", 
                savedPo.getPoNumber(), savedPo.getAmount());
        
        return savedPo;
    }
    
    /**
     * Generates a unique PO number using timestamp and UUID format.
     * Format: PO-YYYYMMDD-HHMMSS-XXXX
     * 
     * @return unique PO number
     */
    private String generatePoNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("PO-%s-%s", timestamp, uniqueId);
    }
    
    /**
     * Retrieves a purchase order by PO number.
     * 
     * @param poNumber the PO number
     * @return Optional containing the PurchaseOrder if found
     */
    public java.util.Optional<PurchaseOrder> getPurchaseOrderByPoNumber(String poNumber) {
        log.debug("Looking up PurchaseOrder for PO: {}", poNumber);
        return purchaseOrderRepository.findByPoNumber(poNumber);
    }
    
    /**
     * Updates the status of a purchase order.
     * 
     * @param poNumber the PO number
     * @param newStatus the new status (e.g., "APPROVED", "REJECTED", "COMPLETED")
     * @return the updated PurchaseOrder
     */
    @Transactional
    public PurchaseOrder updatePurchaseOrderStatus(String poNumber, String newStatus) {
        log.info("Updating PurchaseOrder status for PO: {} to {}", poNumber, newStatus);
        
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new IllegalArgumentException("PurchaseOrder not found for PO: " + poNumber));
        
        purchaseOrder.setStatus(newStatus);
        
        PurchaseOrder updated = purchaseOrderRepository.save(purchaseOrder);
        log.info("PurchaseOrder status updated successfully: PO {}, Status: {}", poNumber, newStatus);
        
        return updated;
    }
}
