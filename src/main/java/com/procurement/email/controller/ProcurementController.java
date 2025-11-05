package com.procurement.email.controller;

import com.procurement.email.model.PurchaseOrder;
import com.procurement.email.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for procurement/purchase order operations.
 * Provides endpoints to retrieve and manage purchase orders.
 */
@RestController
@RequestMapping("/api/procurement")
@RequiredArgsConstructor
@Slf4j
public class ProcurementController {

    private final PurchaseOrderRepository purchaseOrderRepository;

    /**
     * Get all purchase orders.
     * 
     * @return List of all purchase orders
     */
    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> getAllPurchaseOrders() {
        log.info("GET /api/procurement - Fetching all purchase orders");
        List<PurchaseOrder> orders = purchaseOrderRepository.findAll();
        return ResponseEntity.ok(orders);
    }

    /**
     * Get a specific purchase order by PO number.
     * 
     * @param poNumber the PO number (e.g., "PO-2024-001")
     * @return the purchase order if found, 404 if not found
     */
    @GetMapping("/{poNumber}")
    public ResponseEntity<PurchaseOrder> getPurchaseOrderByNumber(@PathVariable String poNumber) {
        log.info("GET /api/procurement/{} - Fetching purchase order by PO number", poNumber);
        return purchaseOrderRepository.findByPoNumber(poNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Purchase order not found: {}", poNumber);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Get purchase orders by requester email.
     * 
     * @param email the requester's email address
     * @return list of purchase orders for the requester
     */
    @GetMapping("/requester/{email}")
    public ResponseEntity<List<PurchaseOrder>> getPurchaseOrdersByRequester(@PathVariable String email) {
        log.info("GET /api/procurement/requester/{} - Fetching purchase orders by requester", email);
        List<PurchaseOrder> orders = purchaseOrderRepository.findByRequesterEmail(email);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get purchase orders by status.
     * 
     * @param status the status (e.g., "PENDING", "APPROVED", "REJECTED", "COMPLETED")
     * @return list of purchase orders with the specified status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<PurchaseOrder>> getPurchaseOrdersByStatus(@PathVariable String status) {
        log.info("GET /api/procurement/status/{} - Fetching purchase orders by status", status);
        List<PurchaseOrder> orders = purchaseOrderRepository.findByStatus(status.toUpperCase());
        return ResponseEntity.ok(orders);
    }

    /**
     * Get recent purchase orders (created in the last N days).
     * 
     * @param days number of days to look back (default: 30)
     * @return list of recent purchase orders
     */
    @GetMapping("/recent")
    public ResponseEntity<List<PurchaseOrder>> getRecentPurchaseOrders(
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/procurement/recent - Fetching purchase orders from last {} days", days);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        List<PurchaseOrder> orders = purchaseOrderRepository.findByCreatedAtAfter(cutoffDate);
        return ResponseEntity.ok(orders);
    }

    /**
     * Get purchase order statistics.
     * 
     * @return statistics including total orders, total amount, status breakdown, etc.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getProcurementStats() {
        log.info("GET /api/procurement/stats - Fetching procurement statistics");
        List<PurchaseOrder> allOrders = purchaseOrderRepository.findAll();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", allOrders.size());
        stats.put("totalAmount", allOrders.stream()
                .map(PurchaseOrder::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        
        // Count by status
        Map<String, Long> byStatus = allOrders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PurchaseOrder::getStatus, 
                        java.util.stream.Collectors.counting()));
        stats.put("ordersByStatus", byStatus);
        
        // Count by item type
        Map<String, Long> byType = allOrders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PurchaseOrder::getItemType, 
                        java.util.stream.Collectors.counting()));
        stats.put("ordersByType", byType);
        
        return ResponseEntity.ok(stats);
    }
}
