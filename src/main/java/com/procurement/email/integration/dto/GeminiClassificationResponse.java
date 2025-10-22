package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Gemini API email classification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiClassificationResponse {
    private boolean isProcurementRelated;
    private String reasoning;
    private double confidenceScore;
}
