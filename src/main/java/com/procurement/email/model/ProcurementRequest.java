package com.procurement.email.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * POJO representing a procurement request extracted from an email.
 */
@Data
public class ProcurementRequest {
    private String requestId;
    private String requesterEmail;
    private String itemType;
    private String itemName;  // Specific item name (e.g., "MacBook Pro")
    private Map<String, String> specifications;
    private Integer quantity;
    private BigDecimal estimatedPrice;  // Estimated unit price
    private String additionalNotes;
    private LocalDateTime requestDate;
}
