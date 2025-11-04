# Testing Qdrant Vector Search with cURL

This guide provides curl commands to test the Qdrant vector search functionality.

## Prerequisites

1. **Start Qdrant in Docker**:
```bash
docker run -d -p 6333:6333 -p 6334:6334 \
    --name qdrant \
    -v $(pwd)/qdrant_storage:/qdrant/storage:z \
    qdrant/qdrant
```

2. **Start the Application**:
```bash
./mvnw spring-boot:run
# or
java -jar target/procurement-email-automation-1.0.0-SNAPSHOT.jar
```

Wait for the application to start and index items. You should see logs like:
```
Embedding Service initialized successfully
Successfully indexed 12 items in vector database
```

## Base URL

```bash
BASE_URL="http://localhost:8080"
```

## 1. Get Similar Items by ID

Find items similar to a specific item (returns just IDs).

### Test with Laptop
```bash
# Find items similar to "LAP-001" (Dell XPS 15)
curl -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-001?limit=5"
```

**Expected Response**:
```json
{
  "itemId": "LAP-001",
  "limit": 5,
  "similarItemIds": ["LAP-003", "LAP-002"],
  "count": 2
}
```

### Test with Monitor
```bash
# Find items similar to "MON-001" (Dell UltraSharp)
curl -X GET "${BASE_URL}/api/inventory/vector/similar/MON-001?limit=3"
```

**Expected Response**:
```json
{
  "itemId": "MON-001",
  "limit": 3,
  "similarItemIds": ["MON-002", "MON-003"],
  "count": 2
}
```

### Test with Accessory
```bash
# Find items similar to "ACC-001" (Logitech MX Keys keyboard)
curl -X GET "${BASE_URL}/api/inventory/vector/similar/ACC-001?limit=5"
```

**Expected Response**:
```json
{
  "itemId": "ACC-001",
  "limit": 5,
  "similarItemIds": ["ACC-002", "ACC-003", "ACC-004"],
  "count": 3
}
```

## 2. Get Similar Items with Full Details

Get complete item information for similar items.

### Test with Laptop (Full Details)
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-001/details?limit=3"
```

**Expected Response** (formatted):
```json
{
  "itemId": "LAP-001",
  "limit": 3,
  "similarItems": [
    {
      "id": "LAP-003",
      "name": "ThinkPad X1",
      "type": "laptop",
      "specifications": {
        "processor": "Intel Core i7-1260P",
        "ram": "16GB DDR4",
        "storage": "256GB SSD",
        "display": "14\" FHD",
        "graphics": "Intel Iris Xe"
      },
      "availableQuantity": 5,
      "bookValue": 1149.99
    },
    {
      "id": "LAP-002",
      "name": "MacBook Pro",
      "type": "laptop",
      "specifications": {
        "processor": "Apple M2 Pro",
        "ram": "16GB Unified Memory",
        "storage": "512GB SSD",
        "display": "14\" Liquid Retina XDR",
        "graphics": "Integrated"
      },
      "availableQuantity": 2,
      "bookValue": 1999.99
    }
  ],
  "count": 2
}
```

### Pretty Print with jq
```bash
curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-001/details?limit=3" | jq '.'
```

## 3. Search by Text Query

Search for items using natural language queries.

### Search for Laptops
```bash
# Search for laptop with specific specs
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=laptop%20with%2016GB%20RAM%20and%20512GB%20SSD&limit=5"
```

**URL-encoded query**: "laptop with 16GB RAM and 512GB SSD"

**Expected Response**:
```json
{
  "query": "laptop with 16GB RAM and 512GB SSD",
  "limit": 5,
  "similarItemIds": ["LAP-001", "LAP-002", "LAP-003"],
  "count": 3
}
```

### Search for Wireless Keyboard
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=wireless%20keyboard&limit=3"
```

**Expected Response**:
```json
{
  "query": "wireless keyboard",
  "limit": 3,
  "similarItemIds": ["ACC-001", "ACC-002"],
  "count": 2
}
```

### Search for Gaming Mouse
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=gaming%20mouse%20high%20DPI&limit=5"
```

**Expected Response**:
```json
{
  "query": "gaming mouse high DPI",
  "limit": 5,
  "similarItemIds": ["ACC-004", "ACC-003"],
  "count": 2
}
```

### Search for 4K Monitor
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=4K%20monitor%20large%20screen&limit=3"
```

**Expected Response**:
```json
{
  "query": "4K monitor large screen",
  "limit": 3,
  "similarItemIds": ["MON-002", "MON-003", "MON-001"],
  "count": 3
}
```

### Search for Headset
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=wireless%20headset%20noise%20cancellation&limit=5"
```

**Expected Response**:
```json
{
  "query": "wireless headset noise cancellation",
  "limit": 5,
  "similarItemIds": ["ACC-005", "ACC-006"],
  "count": 2
}
```

## 4. Reindex All Items

Manually trigger reindexing (useful after bulk updates).

```bash
curl -X POST "${BASE_URL}/api/inventory/vector/reindex"
```

**Expected Response**:
```json
{
  "status": "success",
  "message": "All items have been reindexed in the vector database"
}
```

## 5. Advanced Testing Scenarios

### Test Case 1: Find Similar MacBook Alternatives
```bash
# User wants alternatives to MacBook Pro
curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-002/details?limit=5" | jq '.'
```

### Test Case 2: Semantic Search for "portable computer"
```bash
# Should match laptops even though exact term isn't in descriptions
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=portable%20computer%20for%20work&limit=5"
```

### Test Case 3: Find Similar Accessories to a Mouse
```bash
# Should return other mice or similar pointing devices
curl -X GET "${BASE_URL}/api/inventory/vector/similar/ACC-003?limit=5"
```

### Test Case 4: Search for "ergonomic keyboard"
```bash
# Should match keyboards with comfort features
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=ergonomic%20keyboard%20comfortable&limit=3"
```

### Test Case 5: Find Curved Monitor Alternatives
```bash
curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/MON-003/details?limit=5" | jq '.similarItems[] | {id, name, type}'
```

## 6. Error Handling Tests

### Test with Non-Existent Item
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/similar/INVALID-999?limit=5"
```

**Expected Response**:
```json
{
  "itemId": "INVALID-999",
  "limit": 5,
  "similarItemIds": [],
  "count": 0
}
```

### Test with Empty Query
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/search?query=&limit=5"
```

### Test with Very Large Limit
```bash
curl -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-001?limit=1000"
```

## 7. Batch Testing Script

Save this as `test-vector-search.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=== Testing Vector Search API ==="
echo ""

echo "1. Testing similar items for laptops..."
curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-001?limit=3" | jq '.'
echo ""

echo "2. Testing similar items with details..."
curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/MON-001/details?limit=2" | jq '.'
echo ""

echo "3. Testing text search - wireless keyboard..."
curl -s -X GET "${BASE_URL}/api/inventory/vector/search?query=wireless%20keyboard&limit=3" | jq '.'
echo ""

echo "4. Testing text search - gaming mouse..."
curl -s -X GET "${BASE_URL}/api/inventory/vector/search?query=gaming%20mouse&limit=3" | jq '.'
echo ""

echo "5. Testing text search - 4K display..."
curl -s -X GET "${BASE_URL}/api/inventory/vector/search?query=4K%20display&limit=3" | jq '.'
echo ""

echo "6. Testing with non-existent item..."
curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/INVALID-999?limit=5" | jq '.'
echo ""

echo "=== All tests completed ==="
```

Make it executable and run:
```bash
chmod +x test-vector-search.sh
./test-vector-search.sh
```

## 8. Performance Testing

### Measure Response Time
```bash
time curl -s -X GET "${BASE_URL}/api/inventory/vector/similar/LAP-001?limit=5" > /dev/null
```

### Stress Test (requires Apache Bench)
```bash
# 100 requests, 10 concurrent
ab -n 100 -c 10 "${BASE_URL}/api/inventory/vector/similar/LAP-001?limit=5"
```

## 9. Verify Qdrant Data

### Check Qdrant Collection
```bash
# Get collection info
curl "http://localhost:6333/collections/inventory_items"
```

### Count Points in Collection
```bash
curl "http://localhost:6333/collections/inventory_items" | jq '.result.points_count'
```

**Expected**: 12 (number of inventory items)

### Search Directly in Qdrant
```bash
curl -X POST "http://localhost:6333/collections/inventory_items/points/search" \
  -H "Content-Type: application/json" \
  -d '{
    "vector": [0.1, 0.2, ...],  # 384 dimensions
    "limit": 5
  }'
```

## 10. Integration with Other APIs

### Get Item Details After Search
```bash
# First, search for items
ITEM_IDS=$(curl -s -X GET "${BASE_URL}/api/inventory/vector/search?query=laptop&limit=3" | jq -r '.similarItemIds[]')

# Then fetch details for each item (if you have an item details endpoint)
for id in $ITEM_IDS; do
  echo "Item: $id"
  # curl -X GET "${BASE_URL}/api/inventory/items/$id"
done
```

## Troubleshooting

### Issue: Connection Refused
```bash
# Check if application is running
curl -I http://localhost:8080/actuator/health
```

### Issue: Qdrant Not Running
```bash
# Check Qdrant status
docker ps | grep qdrant

# View Qdrant logs
docker logs qdrant
```

### Issue: No Similar Items Found
```bash
# Trigger reindex
curl -X POST "${BASE_URL}/api/inventory/vector/reindex"

# Check logs
tail -f logs/application.log
```

## Expected Behavior

### Semantic Similarity Examples

The embedding model understands semantic relationships:

1. **Laptops**: LAP-001 (Dell), LAP-002 (MacBook), LAP-003 (ThinkPad) → All similar
2. **Monitors**: MON-001, MON-002, MON-003 → Group together
3. **Keyboards**: ACC-001 (Logitech), ACC-002 (Keychron) → Similar to each other
4. **Mice**: ACC-003 (Logitech), ACC-004 (Razer) → Similar to each other
5. **Headsets**: ACC-005 (Sony), ACC-006 (HyperX) → Similar to each other

### Cross-Category Searches

- "wireless device" → Should match keyboards, mice, headsets
- "display" → Should match monitors
- "computer" → Should match laptops
- "peripheral" → Should match accessories

## Summary

Key endpoints:
- `GET /api/inventory/vector/similar/{itemId}` - Get similar item IDs
- `GET /api/inventory/vector/similar/{itemId}/details` - Get similar items with full details
- `GET /api/inventory/vector/search?query={text}` - Search by text query
- `POST /api/inventory/vector/reindex` - Reindex all items

All endpoints support the `limit` parameter to control result count.
