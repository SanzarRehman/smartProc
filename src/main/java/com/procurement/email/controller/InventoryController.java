package com.procurement.email.controller;

import com.procurement.email.model.Item;
import com.procurement.email.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for inventory management operations.
 * Provides endpoints to retrieve and search inventory items.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Get all available inventory items.
     * 
     * @return List of all items with available quantity > 0
     */
    @GetMapping
    public ResponseEntity<List<Item>> getAllAvailableItems() {
        log.info("GET /api/inventory - Fetching all available items");
        List<Item> items = inventoryService.listAllAvailableItems();
        return ResponseEntity.ok(items);
    }

    /**
     * Get a specific inventory item by ID.
     * 
     * @param itemId the item ID (e.g., "LAP-001", "MON-002", "ACC-003")
     * @return the item if found, 404 if not found
     */
    @GetMapping("/{itemId}")
    public ResponseEntity<Item> getItemById(@PathVariable String itemId) {
        log.info("GET /api/inventory/{} - Fetching item by ID", itemId);
        Item item = inventoryService.getItemById(itemId);
        if (item == null) {
            log.warn("Item not found: {}", itemId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    /**
     * Search inventory items by type and optional specifications.
     * 
     * @param type the item type (e.g., "laptop", "monitor", "accessory", "mouse", "keyboard")
     * @param brand optional brand filter
     * @param model optional model filter
     * @return list of matching items with available quantity > 0
     */
    @GetMapping("/search")
    public ResponseEntity<List<Item>> searchItems(
            @RequestParam String type,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model) {
        
        log.info("GET /api/inventory/search - type: {}, brand: {}, model: {}", type, brand, model);
        
        Map<String, String> specs = new HashMap<>();
        if (brand != null && !brand.isEmpty()) {
            specs.put("brand", brand);
        }
        if (model != null && !model.isEmpty()) {
            specs.put("model", model);
        }
        
        List<Item> items = inventoryService.searchAvailableItems(type, specs);
        return ResponseEntity.ok(items);
    }

    /**
     * Get multiple items by their IDs.
     * 
     * @param ids comma-separated list of item IDs
     * @return list of items found (maintains order where possible)
     */
    @GetMapping("/batch")
    public ResponseEntity<List<Item>> getItemsByIds(@RequestParam String ids) {
        log.info("GET /api/inventory/batch - ids: {}", ids);
        List<String> itemIds = List.of(ids.split(","));
        List<Item> items = inventoryService.getItemsByIds(itemIds);
        return ResponseEntity.ok(items);
    }

    /**
     * Get inventory statistics.
     * 
     * @return statistics including total items, total quantity, etc.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getInventoryStats() {
        log.info("GET /api/inventory/stats - Fetching inventory statistics");
        List<Item> allItems = inventoryService.listAllAvailableItems();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalItems", allItems.size());
        stats.put("totalQuantity", allItems.stream().mapToInt(Item::getAvailableQuantity).sum());
        stats.put("totalValue", allItems.stream()
                .map(item -> item.getBookValue().multiply(new java.math.BigDecimal(item.getAvailableQuantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        
        // Count by type
        Map<String, Long> byType = allItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(Item::getType, java.util.stream.Collectors.counting()));
        stats.put("itemsByType", byType);
        
        return ResponseEntity.ok(stats);
    }
}
