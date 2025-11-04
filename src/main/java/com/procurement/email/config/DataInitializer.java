package com.procurement.email.config;

import com.procurement.email.model.Item;
import com.procurement.email.repository.ItemRepository;
import com.procurement.email.service.InventoryVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Initializes the database with dummy inventory data on application startup.
 * Also populates the Qdrant vector database with inventory items.
 * Only runs if the inventory table is empty.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ItemRepository itemRepository;
    private final InventoryVectorService inventoryVectorService;

    @Override
    public void run(String... args) {
        // Clear Qdrant collection first to avoid duplicates
        log.info("Clearing Qdrant vector database to avoid duplicates...");
        try {
            inventoryVectorService.reindexAllItems();
            log.info("Qdrant vector database cleared");
        } catch (Exception e) {
            log.warn("Could not clear Qdrant collection (may not exist yet): {}", e.getMessage());
        }
        
        // Only initialize if inventory is empty
        if (itemRepository.count() == 0) {
            log.info("Inventory table is empty. Initializing with dummy data...");
            initializeInventory();
            log.info("Inventory initialization complete. Total items: {}", itemRepository.count());
        } else {
            log.info("Inventory table already contains {} items. Skipping SQL initialization.", itemRepository.count());
        }
        
        // Always reindex all items in Qdrant to ensure fresh data
        log.info("Indexing all inventory items in Qdrant vector database...");
        try {
            inventoryVectorService.indexAllItems();
            log.info("Qdrant indexing complete");
        } catch (Exception e) {
            log.error("Error indexing items in Qdrant: {}", e.getMessage(), e);
            log.warn("Application will continue but vector search may not work properly");
        }
    }

    private void initializeInventory() {
        // Laptops
        itemRepository.save(createLaptop("LAP-001", "Dell XPS 15",
                Map.of(
                        "processor", "Intel Core i7-12700H",
                        "ram", "16GB DDR5",
                        "storage", "512GB SSD",
                        "display", "15.6\" FHD",
                        "graphics", "Intel Iris Xe"
                ), 3, new BigDecimal("1299.99")));

        itemRepository.save(createLaptop("LAP-002", "MacBook Pro",
                Map.of(
                        "processor", "Apple M2 Pro",
                        "ram", "16GB Unified Memory",
                        "storage", "512GB SSD",
                        "display", "14\" Liquid Retina XDR",
                        "graphics", "Integrated"
                ), 2, new BigDecimal("1999.99")));

        itemRepository.save(createLaptop("LAP-003", "ThinkPad X1",
                Map.of(
                        "processor", "Intel Core i7-1260P",
                        "ram", "16GB DDR4",
                        "storage", "256GB SSD",
                        "display", "14\" FHD",
                        "graphics", "Intel Iris Xe"
                ), 5, new BigDecimal("1149.99")));

        // Monitors
        itemRepository.save(createMonitor("MON-001", "Dell UltraSharp",
                Map.of(
                        "size", "27\"",
                        "resolution", "2560x1440 QHD",
                        "panel", "IPS",
                        "refresh_rate", "60Hz",
                        "ports", "HDMI, DisplayPort, USB-C"
                ), 4, new BigDecimal("399.99")));

        itemRepository.save(createMonitor("MON-002", "LG 4K",
                Map.of(
                        "size", "32\"",
                        "resolution", "3840x2160 4K",
                        "panel", "IPS",
                        "refresh_rate", "60Hz",
                        "ports", "HDMI, DisplayPort"
                ), 3, new BigDecimal("549.99")));

        itemRepository.save(createMonitor("MON-003", "Samsung Curved",
                Map.of(
                        "size", "34\"",
                        "resolution", "3440x1440 UWQHD",
                        "panel", "VA Curved",
                        "refresh_rate", "100Hz",
                        "ports", "HDMI, DisplayPort, USB-C"
                ), 2, new BigDecimal("649.99")));

        // Accessories - Keyboards
        itemRepository.save(createAccessory("ACC-001", "Logitech MX Keys", "keyboard",
                Map.of(
                        "type", "Wireless Keyboard",
                        "connectivity", "Bluetooth, USB Receiver",
                        "backlight", "Yes",
                        "layout", "Full-size"
                ), 10, new BigDecimal("99.99")));

        itemRepository.save(createAccessory("ACC-002", "Keychron K8", "keyboard",
                Map.of(
                        "type", "Mechanical Keyboard",
                        "connectivity", "Wired/Wireless",
                        "switches", "Gateron Brown",
                        "layout", "TKL"
                ), 8, new BigDecimal("89.99")));

        // Accessories - Mice
        itemRepository.save(createAccessory("ACC-003", "Logitech MX Master 3", "mouse",
                Map.of(
                        "type", "Wireless Mouse",
                        "connectivity", "Bluetooth, USB Receiver",
                        "dpi", "4000",
                        "buttons", "7"
                ), 12, new BigDecimal("99.99")));

        itemRepository.save(createAccessory("ACC-004", "Razer DeathAdder V2", "mouse",
                Map.of(
                        "type", "Gaming Mouse",
                        "connectivity", "Wired",
                        "dpi", "20000",
                        "buttons", "8"
                ), 6, new BigDecimal("69.99")));

        // Accessories - Headsets
        itemRepository.save(createAccessory("ACC-005", "Sony WH-1000XM5", "headset",
                Map.of(
                        "type", "Wireless Headset",
                        "connectivity", "Bluetooth",
                        "noise_cancellation", "Active",
                        "battery_life", "30 hours"
                ), 5, new BigDecimal("399.99")));

        itemRepository.save(createAccessory("ACC-006", "HyperX Cloud II", "headset",
                Map.of(
                        "type", "Gaming Headset",
                        "connectivity", "Wired USB",
                        "surround_sound", "7.1 Virtual",
                        "microphone", "Detachable"
                ), 7, new BigDecimal("99.99")));

        log.info("Added {} inventory items to database", 12);
    }

    private Item createLaptop(String id, String name, Map<String, String> specs,
                              int quantity, BigDecimal bookValue) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setType("laptop");
        item.setSpecifications(new HashMap<>(specs));
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
        item.setSpecifications(new HashMap<>(specs));
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
