package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for Gemini confirmation parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiConfirmationRequest {

    private String senderEmail;
    private String emailSubject;
    private String emailBody;
    private List<CandidateItem> candidateItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateItem {
        private String itemId;
        private String itemName;
    }
}
