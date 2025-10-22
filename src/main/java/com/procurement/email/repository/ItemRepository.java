package com.procurement.email.repository;

import com.procurement.email.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for inventory items.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, String> {
    
    /**
     * Find items by type.
     */
    List<Item> findByTypeIgnoreCase(String type);
    
    /**
     * Find items by type with available quantity greater than 0.
     */
    List<Item> findByTypeIgnoreCaseAndAvailableQuantityGreaterThan(String type, int quantity);
    
    /**
     * Find item by ID (case-insensitive).
     */
    @Query("SELECT i FROM Item i WHERE UPPER(i.id) = UPPER(:id)")
    Optional<Item> findByIdIgnoreCase(@Param("id") String id);
    
    /**
     * Search items by name containing keyword.
     */
    List<Item> findByNameContainingIgnoreCase(String keyword);
}
