package com.procurement.email.repository;

import com.procurement.email.model.Item;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.*;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.ValueFactory.*;

/**
 * Repository for Qdrant vector database operations.
 * Handles indexing inventory items and performing similarity searches.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class QdrantRepository {

    private final QdrantClient qdrantClient;

    @org.springframework.beans.factory.annotation.Value("${qdrant.collection.name:inventory_items}")
    private String collectionName;

    @org.springframework.beans.factory.annotation.Value("${qdrant.vector.size:384}")
    private int vectorSize;

    /**
     * Initializes the Qdrant collection if it doesn't exist.
     * Creates a collection with the specified vector size for embeddings.
     */
    @PostConstruct
    public void initializeCollection() {
        try {
            log.info("Checking if collection '{}' exists...", collectionName);
            
            // Check if collection exists
            List<String> collections = qdrantClient.listCollectionsAsync().get();
            boolean collectionExists = collections.contains(collectionName);

            if (!collectionExists) {
                log.info("Collection '{}' does not exist. Creating...", collectionName);
                
                // Create collection with vector configuration
                VectorParams vectorParams = VectorParams.newBuilder()
                        .setSize(vectorSize)
                        .setDistance(Distance.Cosine)
                        .build();

                qdrantClient.createCollectionAsync(
                        collectionName,
                        vectorParams
                ).get();
                
                log.info("Collection '{}' created successfully with vector size {}", 
                        collectionName, vectorSize);
            } else {
                log.info("Collection '{}' already exists", collectionName);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error initializing Qdrant collection: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to initialize Qdrant collection", e);
        }
    }

    /**
     * Indexes a single item in Qdrant.
     * Concatenates all item fields into a text representation for embedding.
     * 
     * @param item The item to index
     * @param embedding The embedding vector for the item (generated externally)
     */
    public void indexItem(Item item, List<Float> embedding) {
        try {
            String itemText = buildItemText(item);
            log.debug("Indexing item {} with text: {}", item.getId(), itemText);

            // Create point with metadata using ValueFactory
            Map<String, JsonWithInt.Value> payload = new HashMap<>();
            payload.put("item_id", value(item.getId()));
            payload.put("item_name", value(item.getName()));
            payload.put("item_type", value(item.getType()));
            payload.put("item_text", value(itemText));
            payload.put("available_quantity", value(item.getAvailableQuantity()));
            payload.put("book_value", value(item.getBookValue().doubleValue()));

            // Use deterministic UUID based on item ID to avoid duplicates
            UUID pointId = generateDeterministicUUID(item.getId());
            
            PointStruct point = PointStruct.newBuilder()
                    .setId(PointId.newBuilder().setUuid(pointId.toString()).build())
                    .setVectors(Vectors.newBuilder().setVector(
                            io.qdrant.client.grpc.Points.Vector.newBuilder().addAllData(embedding).build()
                    ).build())
                    .putAllPayload(payload)
                    .build();

            qdrantClient.upsertAsync(collectionName, List.of(point)).get();
            log.debug("Successfully indexed item {} in Qdrant with point ID {}", item.getId(), pointId);
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error indexing item {}: {}", item.getId(), e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to index item in Qdrant", e);
        }
    }

    /**
     * Indexes multiple items in a batch.
     * 
     * @param items List of items to index
     * @param embeddings Map of item IDs to their embedding vectors
     */
    public void indexItems(List<Item> items, Map<String, List<Float>> embeddings) {
        try {
            List<PointStruct> points = new ArrayList<>();

            for (Item item : items) {
                List<Float> embedding = embeddings.get(item.getId());
                if (embedding == null) {
                    log.warn("No embedding found for item {}. Skipping.", item.getId());
                    continue;
                }

                String itemText = buildItemText(item);
                
                // Create point with metadata using ValueFactory
                Map<String, JsonWithInt.Value> payload = new HashMap<>();
                payload.put("item_id", value(item.getId()));
                payload.put("item_name", value(item.getName()));
                payload.put("item_type", value(item.getType()));
                payload.put("item_text", value(itemText));
                payload.put("available_quantity", value(item.getAvailableQuantity()));
                payload.put("book_value", value(item.getBookValue().doubleValue()));

                // Use deterministic UUID based on item ID to avoid duplicates
                UUID pointId = generateDeterministicUUID(item.getId());

                PointStruct point = PointStruct.newBuilder()
                        .setId(PointId.newBuilder().setUuid(pointId.toString()).build())
                        .setVectors(Vectors.newBuilder().setVector(
                                io.qdrant.client.grpc.Points.Vector.newBuilder().addAllData(embedding).build()
                        ).build())
                        .putAllPayload(payload)
                        .build();

                points.add(point);
            }

            if (!points.isEmpty()) {
                qdrantClient.upsertAsync(collectionName, points).get();
                log.info("Successfully indexed {} items in Qdrant", points.size());
            }
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error batch indexing items: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to batch index items in Qdrant", e);
        }
    }

    /**
     * Searches for similar items based on a query embedding.
     * 
     * @param queryEmbedding The embedding vector to search for
     * @param limit Maximum number of results to return
     * @return List of similar item IDs, ordered by similarity (filtered by score >= 0.4)
     */
    public List<String> searchSimilarItems(List<Float> queryEmbedding, int limit) {
        try {
            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryEmbedding)
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setScoreThreshold(0.4f)  // Only return items with score >= 0.4
                    .build();

            List<ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();

            List<String> itemIds = results.stream()
                    .filter(scoredPoint -> scoredPoint.getScore() >= 0.4f)  // Additional safety filter
                    .map(scoredPoint -> {
                        JsonWithInt.Value value = scoredPoint.getPayloadMap().get("item_id");
                        String itemId = value != null ? value.getStringValue() : null;
                        log.debug("Found similar item: {} (score: {})", 
                                itemId, scoredPoint.getScore());
                        return itemId;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Found {} similar items with score >= 0.4", itemIds.size());
            return itemIds;
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error searching for similar items: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to search for similar items in Qdrant", e);
        }
    }

    /**
     * Searches for similar items with scores.
     * 
     * @param queryEmbedding The embedding vector to search for
     * @param limit Maximum number of results to return
     * @return Map of item IDs to their similarity scores (filtered by score >= 0.4)
     */
    public Map<String, Float> searchSimilarItemsWithScores(List<Float> queryEmbedding, int limit) {
        try {
            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryEmbedding)
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setScoreThreshold(0.4f)  // Only return items with score >= 0.4
                    .build();

            List<ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();

            Map<String, Float> itemScores = new LinkedHashMap<>();
            for (ScoredPoint scoredPoint : results) {
                // Additional filter to ensure score >= 0.4
                if (scoredPoint.getScore() < 0.4f) {
                    log.debug("Skipping item with low score: {}", scoredPoint.getScore());
                    continue;
                }
                
                JsonWithInt.Value value = scoredPoint.getPayloadMap().get("item_id");
                String itemId = value != null ? value.getStringValue() : null;
                if (itemId != null) {
                    itemScores.put(itemId, scoredPoint.getScore());
                    log.debug("Found similar item: {} (score: {})", itemId, scoredPoint.getScore());
                }
            }

            log.info("Found {} similar items with scores >= 0.4", itemScores.size());
            return itemScores;
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error searching for similar items: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to search for similar items in Qdrant", e);
        }
    }

    /**
     * Clears all points from the collection (useful for reindexing).
     */
    public void clearCollection() {
        try {
            log.info("Clearing all points from collection '{}'", collectionName);
            
            // Delete collection and recreate it (simpler approach)
            qdrantClient.deleteCollectionAsync(collectionName).get();
            
            // Recreate collection
            VectorParams vectorParams = VectorParams.newBuilder()
                    .setSize(vectorSize)
                    .setDistance(Distance.Cosine)
                    .build();

            qdrantClient.createCollectionAsync(collectionName, vectorParams).get();
            
            log.info("Collection '{}' cleared and recreated successfully", collectionName);
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error clearing collection: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to clear Qdrant collection", e);
        }
    }

    /**
     * Builds a text representation of an item by concatenating all fields.
     * This text will be used to generate embeddings.
     * 
     * @param item The item to convert to text
     * @return Concatenated string representation of the item
     */
    private String buildItemText(Item item) {
        StringBuilder text = new StringBuilder();
        
        // Extract subtype if it exists (for accessories like mouse, keyboard, headset)
        String subtype = null;
        if (item.getSpecifications() != null) {
            subtype = item.getSpecifications().get("subtype");
        }
        
        // Emphasize the product category multiple times for better semantic matching
        if (subtype != null) {
            // For accessories, emphasize the subtype (mouse, keyboard, headset)
            text.append(subtype).append(" ").append(subtype).append(" ").append(subtype).append(". ");
            text.append("This is a ").append(subtype).append(". ");
        }
        
        text.append("Product name: ").append(item.getName()).append(". ");
        text.append("Category: ").append(item.getType()).append(". ");
        
        if (subtype != null) {
            text.append("Product type: ").append(subtype).append(". ");
        }
        
        text.append("ID: ").append(item.getId()).append(". ");
        
        if (item.getSpecifications() != null && !item.getSpecifications().isEmpty()) {
            text.append("Specifications: ");
            item.getSpecifications().forEach((key, value) -> {
                // Skip subtype as we already emphasized it
                if (!"subtype".equals(key)) {
                    text.append(key).append(": ").append(value).append(", ");
                }
            });
            // Remove trailing comma and space if there were specs
            if (text.toString().endsWith(", ")) {
                text.setLength(text.length() - 2);
            }
            text.append(". ");
        }
        
        text.append("Available quantity: ").append(item.getAvailableQuantity()).append(". ");
        text.append("Price: $").append(item.getBookValue()).append(".");
        
        return text.toString();
    }

    /**
     * Generates a deterministic UUID from an item ID.
     * This ensures the same item always gets the same point ID in Qdrant,
     * preventing duplicates when re-indexing.
     * 
     * @param itemId The item ID
     * @return Deterministic UUID
     */
    private UUID generateDeterministicUUID(String itemId) {
        // Use UUID v5 (name-based with SHA-1) for deterministic generation
        // Using a fixed namespace UUID for all inventory items
        UUID namespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"); // Standard DNS namespace
        return UUID.nameUUIDFromBytes((namespace.toString() + itemId).getBytes());
    }
}
