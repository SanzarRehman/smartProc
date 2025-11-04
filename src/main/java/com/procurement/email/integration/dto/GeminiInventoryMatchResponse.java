package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Response payload from Gemini inventory matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiInventoryMatchResponse {
    private List<String> matchingItemIds;
    private String reasoning;

    public List<String> getMatchingItemIds() {
        if (matchingItemIds == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(matchingItemIds);
    }
}
