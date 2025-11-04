package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request payload for Gemini inventory matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiInventoryMatchRequest {
    private String itemType;
    private String itemName;
    private Integer quantity;
    private Map<String, String> specifications;
    private List<InventoryItemSummary> inventoryItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItemSummary {
        private String itemId;
        private String itemName;
        private String itemType;
        private Integer availableQuantity;
    }
}
