package com.procurement.email.service;

import com.procurement.email.model.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dummy inventory service that provides mock inventory data for POC demonstration.
 * Returns hardcoded items based on item type.
 */
@Service
@Slf4j
public class InventoryService {

    private final List<Item> mockInventory;

    public InventoryService() {
        this.mockInventory = initializeMockInventory();
    }

    /**
     * Get a specific item by its ID.
     *
     * @param itemId the item ID (e.g., "LAP-001", "MON-002")
     * @return the item if found, null otherwise
     */
    public Item getItemById(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            log.warn("Item ID is null or empty");
            return null;
        }
        
        String normalizedId = itemId.toUpperCase().trim();
        
        return mockInventory.stream()
                .filter(item -> item.getId().equalsIgnoreCase(normalizedId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Search for available items matching the specified item type and specifications.
     * Performs flexible matching - searches by type first, then filters by name/specs if provided.
     *
     * @param itemType the type of item to search for (e.g., "laptop", "monitor", "accessories")
     * @param specs optional specifications to filter items (can be null)
     * @return list of matching items from mock inventory
     */
    public List<Item> searchAvailableItems(String itemType, Map<String, String> specs) {
        log.info("Searching inventory for item type: {}, specs: {}", itemType, specs);
        
        if (itemType == null || itemType.trim().isEmpty()) {
            log.warn("Item type is null or empty, returning empty list");
            return Collections.emptyList();
        }

        String normalizedType = itemType.toLowerCase().trim();
        
        // First, filter by type and availability
        List<Item> matchingItems = mockInventory.stream()
                .filter(item -> item.getType().equalsIgnoreCase(normalizedType))
                .filter(item -> item.getAvailableQuantity() > 0)
                .collect(Collectors.toList());
        
        // If specs contain brand/model info, try to match by name too
        if (specs != null && !specs.isEmpty()) {
            String brand = specs.getOrDefault("brand", "").toLowerCase();
            String model = specs.getOrDefault("model", "").toLowerCase();
            
            if (!brand.isEmpty() || !model.isEmpty()) {
                // Filter to items that match brand or model in their name
                List<Item> brandModelMatches = matchingItems.stream()
                        .filter(item -> {
                            String itemName = item.getName().toLowerCase();
                            boolean matches = false;
                            if (!brand.isEmpty() && itemName.contains(brand)) {
                                matches = true;
                            }
                            if (!model.isEmpty() && itemName.contains(model)) {
                                matches = true;
                            }
                            return matches;
                        })
                        .collect(Collectors.toList());
                
                // If we found specific matches, use those; otherwise return all type matches
                if (!brandModelMatches.isEmpty()) {
                    matchingItems = brandModelMatches;
                    log.info("Filtered to {} items matching brand/model", matchingItems.size());
                }
            }
        }

        log.info("Found {} matching items for type: {}", matchingItems.size(), itemType);
        return matchingItems;
    }

    /**
     * Initialize mock inventory with laptops, monitors, and accessories.
     *
     * @return list of mock inventory items
     */
    private List<Item> initializeMockInventory() {
        List<Item> inventory = new ArrayList<>();

        // Laptops
        inventory.add(createLaptop("LAP-001", "Dell XPS 15", 
                Map.of(
                        "processor", "Intel Core i7-12700H",
                        "ram", "16GB DDR5",
                        "storage", "512GB SSD",
                        "display", "15.6\" FHD",
                        "graphics", "Intel Iris Xe"
                ), 3, new BigDecimal("1299.99")));

        inventory.add(createLaptop("LAP-002", "MacBook Pro", 
                Map.of(
                        "processor", "Apple M2 Pro",
                        "ram", "16GB Unified Memory",
                        "storage", "512GB SSD",
                        "display", "14\" Liquid Retina XDR",
                        "graphics", "Integrated"
                ), 2, new BigDecimal("1999.99")));

        inventory.add(createLaptop("LAP-003", "ThinkPad X1", 
                Map.of(
                        "processor", "Intel Core i7-1260P",
                        "ram", "16GB DDR4",
                        "storage", "256GB SSD",
                        "display", "14\" FHD",
                        "graphics", "Intel Iris Xe"
                ), 5, new BigDecimal("1149.99")));

        // Monitors
        inventory.add(createMonitor("MON-001", "Dell UltraSharp", 
                Map.of(
                        "size", "27\"",
                        "resolution", "2560x1440 QHD",
                        "panel", "IPS",
                        "refresh_rate", "60Hz",
                        "ports", "HDMI, DisplayPort, USB-C"
                ), 4, new BigDecimal("399.99")));

        inventory.add(createMonitor("MON-002", "LG 4K", 
                Map.of(
                        "size", "32\"",
                        "resolution", "3840x2160 4K",
                        "panel", "IPS",
                        "refresh_rate", "60Hz",
                        "ports", "HDMI, DisplayPort"
                ), 3, new BigDecimal("549.99")));

        inventory.add(createMonitor("MON-003", "Samsung Curved", 
                Map.of(
                        "size", "34\"",
                        "resolution", "3440x1440 UWQHD",
                        "panel", "VA Curved",
                        "refresh_rate", "100Hz",
                        "ports", "HDMI, DisplayPort, USB-C"
                ), 2, new BigDecimal("649.99")));

        // Accessories - Keyboards
        inventory.add(createAccessory("ACC-001", "Logitech MX Keys", "keyboard",
                Map.of(
                        "type", "Wireless Keyboard",
                        "connectivity", "Bluetooth, USB Receiver",
                        "backlight", "Yes",
                        "layout", "Full-size"
                ), 10, new BigDecimal("99.99")));

        inventory.add(createAccessory("ACC-002", "Keychron K8", "keyboard",
                Map.of(
                        "type", "Mechanical Keyboard",
                        "connectivity", "Wired/Wireless",
                        "switches", "Gateron Brown",
                        "layout", "TKL"
                ), 8, new BigDecimal("89.99")));

        // Accessories - Mice
        inventory.add(createAccessory("ACC-003", "Logitech MX Master 3", "mouse",
                Map.of(
                        "type", "Wireless Mouse",
                        "connectivity", "Bluetooth, USB Receiver",
                        "dpi", "4000",
                        "buttons", "7"
                ), 12, new BigDecimal("99.99")));

        inventory.add(createAccessory("ACC-004", "Razer DeathAdder V2", "mouse",
                Map.of(
                        "type", "Gaming Mouse",
                        "connectivity", "Wired",
                        "dpi", "20000",
                        "buttons", "8"
                ), 6, new BigDecimal("69.99")));

        // Accessories - Headsets
        inventory.add(createAccessory("ACC-005", "Sony WH-1000XM5", "headset",
                Map.of(
                        "type", "Wireless Headset",
                        "connectivity", "Bluetooth",
                        "noise_cancellation", "Active",
                        "battery_life", "30 hours"
                ), 5, new BigDecimal("399.99")));

        inventory.add(createAccessory("ACC-006", "HyperX Cloud II", "headset",
                Map.of(
                        "type", "Gaming Headset",
                        "connectivity", "Wired USB",
                        "surround_sound", "7.1 Virtual",
                        "microphone", "Detachable"
                ), 7, new BigDecimal("99.99")));

        log.info("Initialized mock inventory with {} items", inventory.size());
        return inventory;
    }

    private Item createLaptop(String id, String name, Map<String, String> specs, 
                              int quantity, BigDecimal bookValue) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setType("laptop");
        item.setSpecifications(specs);
        item.setAvailableQuantity(quantity);
        item.setBookValue(bookValue);
        return item;
    }

    private Item createMonitor(String id, String name, Map<String, String> specs, 
                               int quantity, BigDecimal bookValue) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setType("monitor");
        item.setSpecifications(specs);
        item.setAvailableQuantity(quantity);
        item.setBookValue(bookValue);
        return item;
    }

    private Item createAccessory(String id, String name, String subType, 
                                 Map<String, String> specs, int quantity, BigDecimal bookValue) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setType("accessories");
        item.setSpecifications(new HashMap<>(specs));
        item.getSpecifications().put("subtype", subType);
        item.setAvailableQuantity(quantity);
        item.setBookValue(bookValue);
        return item;
    }
}
