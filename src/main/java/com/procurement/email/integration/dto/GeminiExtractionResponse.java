package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for Gemini API procurement request extraction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiExtractionResponse {
    private String itemType;
    private String itemName;  // Specific item name (e.g., "MacBook Pro")
    private Map<String, String> specifications;
    private Integer quantity;
    private BigDecimal estimatedPrice;  // Estimated unit price
    private String additionalNotes;
}
