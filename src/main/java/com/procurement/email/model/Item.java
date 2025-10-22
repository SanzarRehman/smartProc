package com.procurement.email.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * JPA entity representing an inventory item.
 */
@Entity
@Table(name = "inventory_items")
@Data
public class Item {
    
    @Id
    @Column(name = "item_id", nullable = false, unique = true)
    private String id;
    
    @Column(name = "item_name", nullable = false)
    private String name;
    
    @Column(name = "item_type", nullable = false)
    private String type;
    
    @ElementCollection
    @CollectionTable(name = "item_specifications", joinColumns = @JoinColumn(name = "item_id"))
    @MapKeyColumn(name = "spec_key")
    @Column(name = "spec_value")
    private Map<String, String> specifications;
    
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;
    
    @Column(name = "book_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal bookValue;
}
