package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Gemini confirmation parsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiConfirmationResponse {

    private boolean confirmed;
    private List<String> selectedItemIds;
    private String reasoning;
}
