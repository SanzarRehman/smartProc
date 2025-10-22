package com.procurement.email.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * POJO representing an inventory item.
 */
@Data
public class Item {
    private String id;
    private String name;
    private String type;
    private Map<String, String> specifications;
    private int availableQuantity;
    private BigDecimal bookValue;
}
