package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Gemini API email classification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiClassificationRequest {
    private String emailSubject;
    private String emailBody;
    private String senderEmail;
}
