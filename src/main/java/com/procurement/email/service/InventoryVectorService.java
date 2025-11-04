package com.procurement.email.service;

import ai.onnxruntime.OrtException;
import com.procurement.email.model.Item;
import com.procurement.email.repository.ItemRepository;
import com.procurement.email.repository.QdrantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing inventory items in the vector database
 * and performing similarity searches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryVectorService {

    private final QdrantRepository qdrantRepository;
    private final ItemRepository itemRepository;
    private final EmbeddingService embeddingService;

    /**
     * Indexes a single item in the vector database.
     * 
     * @param item The item to index
     */
    @Transactional(readOnly = true)
    public void indexItem(Item item) throws OrtException {
        log.info("Indexing item {} in vector database", item.getId());
        
        String itemText = buildItemText(item);

        List<Float> embedding = embeddingService.generateEmbedding(itemText);

        qdrantRepository.indexItem(item, embedding);
        log.info("Successfully indexed item {}", item.getId());
    }

    /**
     * Indexes all items from the database into the vector database.
     * This is typically called during application startup.
     */
    @Transactional(readOnly = true)
    public void indexAllItems() throws OrtException {
        log.info("Starting to index all inventory items in vector database");
        
        List<Item> items = itemRepository.findAll();
        if (items.isEmpty()) {
            log.warn("No items found in database to index");
            return;
        }

        Map<String, List<Float>> embeddings = new HashMap<>();
        
        for (Item item : items) {
            String itemText = buildItemText(item);
            List<Float> embedding = embeddingService.generateEmbedding(itemText);
            embeddings.put(item.getId(), embedding);
        }

        qdrantRepository.indexItems(items, embeddings);
        log.info("Successfully indexed {} items in vector database", items.size());
    }

    /**
     * Gets a list of similar item IDs based on the given item ID.
     * 
     * @param itemId The ID of the item to find similar items for
     * @param limit Maximum number of similar items to return
     * @return List of similar item IDs (excluding the query item itself)
     */
    @Transactional(readOnly = true)
    public List<String> getSimilarItemIds(String itemId, int limit) throws OrtException {
        log.info("Finding similar items for item: {}", itemId);
        
        // Get the item from database
        Optional<Item> itemOpt = itemRepository.findByIdIgnoreCase(itemId);
        if (itemOpt.isEmpty()) {
            log.warn("Item not found: {}", itemId);
            return Collections.emptyList();
        }

        Item item = itemOpt.get();
        
        // Generate embedding for the query item
        String itemText = buildItemText(item);
        List<Float> queryEmbedding = embeddingService.generateEmbedding(itemText);
        
        // Search for similar items (request limit + 1 to account for the query item itself)
        List<String> similarIds = qdrantRepository.searchSimilarItems(queryEmbedding, limit + 1);
        
        // Remove the query item itself from results
        List<String> filteredIds = similarIds.stream()
                .filter(id -> !id.equalsIgnoreCase(itemId))
                .limit(limit)
                .collect(Collectors.toList());
        
        log.info("Found {} similar items for item {}", filteredIds.size(), itemId);
        return filteredIds;
    }

    /**
     * Gets similar items with full details.
     * 
     * @param itemId The ID of the item to find similar items for
     * @param limit Maximum number of similar items to return
     * @return List of similar Item objects
     */
    @Transactional(readOnly = true)
    public List<Item> getSimilarItems(String itemId, int limit) throws OrtException {
        List<String> similarIds = getSimilarItemIds(itemId, limit);
        
        if (similarIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch full item details
        List<Item> similarItems = new ArrayList<>();
        for (String id : similarIds) {
            itemRepository.findByIdIgnoreCase(id).ifPresent(similarItems::add);
        }
        
        return similarItems;
    }

    /**
     * Searches for items similar to a given text query.
     * 
     * @param query The search query text
     * @param limit Maximum number of results to return
     * @return List of similar item IDs
     */
    public List<String> searchSimilarItemsByText(String query, int limit) throws OrtException {
        log.info("Searching for items similar to query: '{}'", query);
        
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);
        List<String> similarIds = qdrantRepository.searchSimilarItems(queryEmbedding, limit);
        
        log.info("Found {} items matching query", similarIds.size());
        return similarIds;
    }

    /**
     * Searches for items similar to a given text query with similarity scores.
     * 
     * @param query The search query text
     * @param limit Maximum number of results to return
     * @return Map of item IDs to their similarity scores
     */
    public Map<String, Float> searchSimilarItemsByTextWithScores(String query, int limit) throws OrtException {
        log.info("Searching for items similar to query: '{}'", query);
        
        List<Float> queryEmbedding = embeddingService.generateEmbedding(query);
        Map<String, Float> itemScores = qdrantRepository.searchSimilarItemsWithScores(queryEmbedding, limit);
        
        log.info("Found {} items matching query with scores", itemScores.size());
        return itemScores;
    }

    /**
     * Reindexes all items (clears and rebuilds the vector database).
     * Useful when items have been updated or the embedding model changes.
     */
    @Transactional(readOnly = true)
    public void reindexAllItems() throws OrtException {
        log.info("Reindexing all inventory items");
        
        qdrantRepository.clearCollection();
        indexAllItems();
        
        log.info("Reindexing completed");
    }

    /**
     * Builds a text representation of an item by concatenating all fields.
     * This matches the format used in QdrantRepository.
     * 
     * @param item The item to convert to text
     * @return Concatenated string representation of the item
     */
    private String buildItemText(Item item) {
        StringBuilder text = new StringBuilder();
        
        text.append("ID: ").append(item.getId()).append(". ");
        text.append("Name: ").append(item.getName()).append(". ");
        text.append("Type: ").append(item.getType()).append(". ");
        
        if (item.getSpecifications() != null && !item.getSpecifications().isEmpty()) {
            text.append("Specifications: ");
            item.getSpecifications().forEach((key, value) -> 
                    text.append(key).append(": ").append(value).append(", ")
            );
            // Remove trailing comma and space
            text.setLength(text.length() - 2);
            text.append(". ");
        }
        
        text.append("Quantity: ").append(item.getAvailableQuantity()).append(". ");
        text.append("Value: $").append(item.getBookValue());
        
        return text.toString();
    }
}
