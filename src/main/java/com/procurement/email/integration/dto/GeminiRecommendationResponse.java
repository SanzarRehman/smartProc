package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Gemini API item recommendation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRecommendationResponse {
    private List<RecommendedItem> recommendedItems;
    private String reasoning;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedItem {
        private String itemId;
        private String itemName;
        private double matchScore;
        private String matchReason;
    }
}
