package com.procurement.email.integration.dto;

import com.procurement.email.model.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for Gemini API item recommendation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRecommendationRequest {
    private String itemType;
    private Map<String, String> requestedSpecifications;
    private Integer quantity;
    private String userRole;
    private String userDesignation;
    private Map<String, String> userPreferences;
    private List<Item> availableItems;
}
