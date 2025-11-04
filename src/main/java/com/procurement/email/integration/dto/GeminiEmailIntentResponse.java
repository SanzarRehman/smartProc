package com.procurement.email.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response DTO representing Gemini's understanding of an email's intent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiEmailIntentResponse {

    public enum IntentType {
        NEW_REQUEST_WITH_DETAILS,
        NEW_REQUEST_MISSING_DETAILS,
        REPLY_CONFIRMATION,
        REPLY_INFORMATION,
        NOT_PROCUREMENT
    }

    private boolean procurementRelated;
    private boolean reply;
    private boolean hasAllDetails;
    private IntentType intentType;
    private List<String> missingDetails;
    private String reasoning;

    public List<String> getMissingDetails() {
        if (missingDetails == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(missingDetails));
    }
}
