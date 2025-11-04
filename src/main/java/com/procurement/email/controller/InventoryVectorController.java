package com.procurement.email.controller;

import ai.onnxruntime.OrtException;
import com.procurement.email.model.Item;
import com.procurement.email.service.InventoryVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for inventory vector search operations.
 * Provides endpoints for finding similar items using Qdrant vector database.
 */
@RestController
@RequestMapping("/api/inventory/vector")
@RequiredArgsConstructor
@Slf4j
public class InventoryVectorController {

    private final InventoryVectorService inventoryVectorService;

    /**
     * Get similar item IDs based on a given item ID.
     * 
     * @param itemId The ID of the item to find similar items for
     * @param limit Maximum number of similar items to return (default: 5)
     * @return List of similar item IDs
     */
    @GetMapping("/similar/{itemId}")
    public ResponseEntity<Map<String, Object>> getSimilarItemIds(
            @PathVariable String itemId,
            @RequestParam(defaultValue = "5") int limit) throws OrtException {
        
        log.info("REST: Getting similar items for item: {}, limit: {}", itemId, limit);
        
        List<String> similarIds = inventoryVectorService.getSimilarItemIds(itemId, limit);
        
        return ResponseEntity.ok(Map.of(
                "itemId", itemId,
                "limit", limit,
                "similarItemIds", similarIds,
                "count", similarIds.size()
        ));
    }

    /**
     * Get similar items with full details based on a given item ID.
     * 
     * @param itemId The ID of the item to find similar items for
     * @param limit Maximum number of similar items to return (default: 5)
     * @return List of similar Item objects
     */
    @GetMapping("/similar/{itemId}/details")
    public ResponseEntity<Map<String, Object>> getSimilarItems(
            @PathVariable String itemId,
            @RequestParam(defaultValue = "5") int limit) throws OrtException {
        
        log.info("REST: Getting similar items with details for item: {}, limit: {}", itemId, limit);
        
        List<Item> similarItems = inventoryVectorService.getSimilarItems(itemId, limit);
        
        return ResponseEntity.ok(Map.of(
                "itemId", itemId,
                "limit", limit,
                "similarItems", similarItems,
                "count", similarItems.size()
        ));
    }

    /**
     * Search for items similar to a text query.
     * 
     * @param query The search query text
     * @param limit Maximum number of results to return (default: 5)
     * @return List of similar item IDs
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchSimilarItems(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) throws OrtException {
        
        log.info("REST: Searching for items similar to query: '{}', limit: {}", query, limit);
        
        List<String> similarIds = inventoryVectorService.searchSimilarItemsByText(query, limit);
        
        return ResponseEntity.ok(Map.of(
                "query", query,
                "limit", limit,
                "similarItemIds", similarIds,
                "count", similarIds.size()
        ));
    }

    /**
     * Search for items similar to a text query with similarity scores.
     * 
     * @param query The search query text
     * @param limit Maximum number of results to return (default: 5)
     * @return Map of item IDs to similarity scores
     */
    @GetMapping("/search/scores")
    public ResponseEntity<Map<String, Object>> searchSimilarItemsWithScores(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) throws OrtException {
        
        log.info("REST: Searching for items with scores similar to query: '{}', limit: {}", query, limit);
        
        Map<String, Float> itemScores = inventoryVectorService.searchSimilarItemsByTextWithScores(query, limit);
        
        return ResponseEntity.ok(Map.of(
                "query", query,
                "limit", limit,
                "results", itemScores,
                "count", itemScores.size()
        ));
    }

    /**
     * Reindex all items in the vector database.
     * Useful for maintenance or after bulk updates.
     * 
     * @return Status message
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindexAllItems() {
        log.info("REST: Reindexing all items in vector database");
        
        try {
            inventoryVectorService.reindexAllItems();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "All items have been reindexed in the vector database"
            ));
        } catch (Exception e) {
            log.error("Error reindexing items: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to reindex items: " + e.getMessage()
            ));
        }
    }
}
