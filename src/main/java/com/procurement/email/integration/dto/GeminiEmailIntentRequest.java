package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for analyzing email intent via Gemini.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiEmailIntentRequest {
    private String senderEmail;
    private String emailSubject;
    private String emailBody;
    private String inReplyToMessageId;
}
