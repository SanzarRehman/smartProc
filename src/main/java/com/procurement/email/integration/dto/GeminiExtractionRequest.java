package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Gemini API procurement request extraction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiExtractionRequest {
    private String emailSubject;
    private String emailBody;
    private String senderEmail;
}
