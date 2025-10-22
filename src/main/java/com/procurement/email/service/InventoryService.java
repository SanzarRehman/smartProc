package com.procurement.email.service;

import com.procurement.email.model.Item;
import com.procurement.email.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Inventory service that manages items in the database.
 * Provides methods to search and retrieve inventory items.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryService {

    private final ItemRepository itemRepository;

    /**
     * Get a specific item by its ID.
     *
     * @param itemId the item ID (e.g., "LAP-001", "MON-002")
     * @return the item if found, null otherwise
     */
    public Item getItemById(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            log.warn("Item ID is null or empty");
            return null;
        }
        
        return itemRepository.findByIdIgnoreCase(itemId).orElse(null);
    }
    
    /**
     * Search for available items matching the specified item type and specifications.
     * Performs flexible matching - searches by type first, then filters by name/specs if provided.
     *
     * @param itemType the type of item to search for (e.g., "laptop", "monitor", "accessories")
     * @param specs optional specifications to filter items (can be null)
     * @return list of matching items from database
     */
    public List<Item> searchAvailableItems(String itemType, Map<String, String> specs) {
        log.info("Searching inventory for item type: {}, specs: {}", itemType, specs);
        
        if (itemType == null || itemType.trim().isEmpty()) {
            log.warn("Item type is null or empty, returning empty list");
            return Collections.emptyList();
        }

        String normalizedType = itemType.toLowerCase().trim();
        
        // First, filter by type and availability from database
        List<Item> matchingItems = itemRepository.findByTypeIgnoreCaseAndAvailableQuantityGreaterThan(normalizedType, 0);
        
        // If specs contain brand/model info, try to match by name too
        if (specs != null && !specs.isEmpty()) {
            String brand = specs.getOrDefault("brand", "").toLowerCase();
            String model = specs.getOrDefault("model", "").toLowerCase();
            
            if (!brand.isEmpty() || !model.isEmpty()) {
                // Filter to items that match brand or model in their name
                List<Item> brandModelMatches = matchingItems.stream()
                        .filter(item -> {
                            String itemName = item.getName().toLowerCase();
                            boolean matches = false;
                            if (!brand.isEmpty() && itemName.contains(brand)) {
                                matches = true;
                            }
                            if (!model.isEmpty() && itemName.contains(model)) {
                                matches = true;
                            }
                            return matches;
                        })
                        .collect(Collectors.toList());
                
                // If we found specific matches, use those; otherwise return all type matches
                if (!brandModelMatches.isEmpty()) {
                    matchingItems = brandModelMatches;
                    log.info("Filtered to {} items matching brand/model", matchingItems.size());
                }
            }
        }

        log.info("Found {} matching items for type: {}", matchingItems.size(), itemType);
        return matchingItems;
    }
    
    /**
     * Update item quantity after allocation.
     */
    @Transactional
    public void decrementItemQuantity(String itemId, int quantity) {
        Item item = getItemById(itemId);
        if (item != null) {
            int newQuantity = item.getAvailableQuantity() - quantity;
            if (newQuantity < 0) {
                log.warn("Item {} quantity would go negative: {} - {} = {}", 
                        itemId, item.getAvailableQuantity(), quantity, newQuantity);
                newQuantity = 0;
            }
            item.setAvailableQuantity(newQuantity);
            itemRepository.save(item);
            log.info("Updated item {} quantity: {} -> {}", itemId, item.getAvailableQuantity() + quantity, newQuantity);
        }
    }

}
