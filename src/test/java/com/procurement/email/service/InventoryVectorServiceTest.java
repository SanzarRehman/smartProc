package com.procurement.email.service;

import ai.onnxruntime.OrtException;
import com.procurement.email.model.Item;
import com.procurement.email.repository.ItemRepository;
import com.procurement.email.repository.QdrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryVectorService.
 */
@ExtendWith(MockitoExtension.class)
class InventoryVectorServiceTest {

    @Mock
    private QdrantRepository qdrantRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private InventoryVectorService inventoryVectorService;

    private Item testItem;
    private List<Float> testEmbedding;

    @BeforeEach
    void setUp() {
        testItem = new Item();
        testItem.setId("TEST-001");
        testItem.setName("Test Item");
        testItem.setType("laptop");
        testItem.setSpecifications(Map.of("ram", "16GB", "storage", "512GB"));
        testItem.setAvailableQuantity(5);
        testItem.setBookValue(new BigDecimal("999.99"));

        testEmbedding = new ArrayList<>();
        for (int i = 0; i < 384; i++) {
            testEmbedding.add(0.1f);
        }
    }

    @Test
    void testIndexItem() throws OrtException {
        // Arrange
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding);
        doNothing().when(qdrantRepository).indexItem(any(Item.class), anyList());

        // Act
        inventoryVectorService.indexItem(testItem);

        // Assert
        verify(embeddingService).generateEmbedding(anyString());
        verify(qdrantRepository).indexItem(eq(testItem), eq(testEmbedding));
    }

    @Test
    void testIndexAllItems() throws OrtException {
        // Arrange
        List<Item> items = Arrays.asList(testItem);
        when(itemRepository.findAll()).thenReturn(items);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding);
        doNothing().when(qdrantRepository).indexItems(anyList(), anyMap());

        // Act
        inventoryVectorService.indexAllItems();

        // Assert
        verify(itemRepository).findAll();
        verify(embeddingService).generateEmbedding(anyString());
        verify(qdrantRepository).indexItems(eq(items), anyMap());
    }

    @Test
    void testGetSimilarItemIds() throws OrtException {
        // Arrange
        String queryItemId = "TEST-001";
        List<String> mockSimilarIds = Arrays.asList("TEST-001", "TEST-002", "TEST-003");
        
        when(itemRepository.findByIdIgnoreCase(queryItemId)).thenReturn(Optional.of(testItem));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding);
        when(qdrantRepository.searchSimilarItems(anyList(), anyInt())).thenReturn(mockSimilarIds);

        // Act
        List<String> result = inventoryVectorService.getSimilarItemIds(queryItemId, 2);

        // Assert
        assertEquals(2, result.size());
        assertFalse(result.contains(queryItemId)); // Should exclude query item itself
        assertTrue(result.contains("TEST-002"));
        assertTrue(result.contains("TEST-003"));
    }

    @Test
    void testGetSimilarItemIds_ItemNotFound() throws OrtException {
        // Arrange
        String queryItemId = "NONEXISTENT";
        when(itemRepository.findByIdIgnoreCase(queryItemId)).thenReturn(Optional.empty());

        // Act
        List<String> result = inventoryVectorService.getSimilarItemIds(queryItemId, 5);

        // Assert
        assertTrue(result.isEmpty());
        verify(qdrantRepository, never()).searchSimilarItems(anyList(), anyInt());
    }

    @Test
    void testSearchSimilarItemsByText() throws OrtException {
        // Arrange
        String query = "laptop with 16GB RAM";
        List<String> mockResults = Arrays.asList("LAP-001", "LAP-002");
        
        when(embeddingService.generateEmbedding(query)).thenReturn(testEmbedding);
        when(qdrantRepository.searchSimilarItems(testEmbedding, 5)).thenReturn(mockResults);

        // Act
        List<String> result = inventoryVectorService.searchSimilarItemsByText(query, 5);

        // Assert
        assertEquals(2, result.size());
        assertEquals(mockResults, result);
        verify(embeddingService).generateEmbedding(query);
        verify(qdrantRepository).searchSimilarItems(testEmbedding, 5);
    }

    @Test
    void testGetSimilarItems() throws OrtException {
        // Arrange
        String queryItemId = "TEST-001";
        Item similarItem = new Item();
        similarItem.setId("TEST-002");
        similarItem.setName("Similar Item");
        
        List<String> mockSimilarIds = Arrays.asList("TEST-001", "TEST-002");
        
        when(itemRepository.findByIdIgnoreCase(queryItemId)).thenReturn(Optional.of(testItem));
        when(itemRepository.findByIdIgnoreCase("TEST-002")).thenReturn(Optional.of(similarItem));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding);
        when(qdrantRepository.searchSimilarItems(anyList(), anyInt())).thenReturn(mockSimilarIds);

        // Act
        List<Item> result = inventoryVectorService.getSimilarItems(queryItemId, 5);

        // Assert
        assertEquals(1, result.size());
        assertEquals("TEST-002", result.get(0).getId());
    }

    @Test
    void testReindexAllItems() throws OrtException {
        // Arrange
        List<Item> items = Arrays.asList(testItem);
        when(itemRepository.findAll()).thenReturn(items);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(testEmbedding);
        doNothing().when(qdrantRepository).clearCollection();
        doNothing().when(qdrantRepository).indexItems(anyList(), anyMap());

        // Act
        inventoryVectorService.reindexAllItems();

        // Assert
        verify(qdrantRepository).clearCollection();
        verify(itemRepository).findAll();
        verify(qdrantRepository).indexItems(eq(items), anyMap());
    }
}
