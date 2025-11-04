# Embedding Model Integration

## Overview

The application now uses **Sentence Transformers (all-MiniLM-L6-v2)** for generating high-quality text embeddings. This replaces the previous hash-based approach with a real machine learning model.

## Model Details

### Sentence Transformers: all-MiniLM-L6-v2

- **Model**: sentence-transformers/all-MiniLM-L6-v2
- **Type**: BERT-based sentence embedding model
- **Embedding Size**: 384 dimensions
- **Max Sequence Length**: 256 tokens
- **License**: Apache 2.0 (Free for commercial use)
- **Performance**: 
  - Speed: ~2000 sentences/second on CPU
  - Quality: High semantic similarity accuracy
  - Size: ~23 MB (ONNX format)

### Why This Model?

1. **Optimized for Similarity Search**: Specifically trained for semantic similarity tasks
2. **Fast**: Runs efficiently on CPU without GPU requirements
3. **Lightweight**: Small model size (~23 MB)
4. **Pre-trained**: No training required, works out of the box
5. **Open Source**: Free to use with permissive license

## Technology Stack

### ONNX Runtime
- **Purpose**: Efficient inference engine for running ML models
- **Benefits**:
  - Cross-platform (Windows, Linux, macOS)
  - Optimized for production
  - No Python/TensorFlow dependencies
  - Fast CPU inference

### HuggingFace Tokenizers (DJL)
- **Purpose**: Text preprocessing and tokenization
- **Benefits**:
  - Fast tokenization in Java
  - Compatible with HuggingFace models
  - Production-ready

## How It Works

```
Input Text
    ↓
Tokenization (HuggingFace Tokenizer)
    ↓
Token IDs, Attention Mask, Token Type IDs
    ↓
ONNX Model Inference (all-MiniLM-L6-v2)
    ↓
Token Embeddings (256 tokens × 384 dims)
    ↓
Mean Pooling (average valid tokens)
    ↓
L2 Normalization
    ↓
Final Embedding Vector (384 dimensions)
```

## Configuration

### Application Properties

In `application.yml`:

```yaml
embedding:
  model:
    url: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx
  tokenizer:
    url: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json
  cache:
    dir: ${java.io.tmpdir}/embedding-models
```

### Environment Variables

```bash
# Override model URL (for custom models)
export EMBEDDING_MODEL_URL=https://your-custom-model.onnx

# Override tokenizer URL
export EMBEDDING_TOKENIZER_URL=https://your-custom-tokenizer.json

# Override cache directory
export EMBEDDING_CACHE_DIR=/var/cache/embeddings
```

## Initialization

### Automatic Download

On first startup, the application will:
1. Create the cache directory
2. Download the ONNX model (~23 MB)
3. Download the tokenizer (~2 MB)
4. Initialize ONNX Runtime
5. Load the model into memory

**Note**: First startup takes ~30-60 seconds depending on internet speed. Subsequent startups use cached files and take ~2-3 seconds.

### Manual Download (Optional)

For air-gapped environments, download files manually:

```bash
# Create cache directory
mkdir -p /tmp/embedding-models

# Download model
curl -L "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx" \
     -o /tmp/embedding-models/model.onnx

# Download tokenizer
curl -L "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json" \
     -o /tmp/embedding-models/tokenizer.json
```

## Usage

### Generating Embeddings

```java
@Autowired
private EmbeddingService embeddingService;

// Generate embedding for text
String text = "Dell XPS laptop with 16GB RAM and 512GB SSD";
List<Float> embedding = embeddingService.generateEmbedding(text);

// Embedding is a 384-dimensional vector
System.out.println("Embedding size: " + embedding.size()); // 384
```

### Example: Item Embedding

For an inventory item:

```java
Item item = itemRepository.findById("LAP-001").get();
String itemText = "ID: LAP-001. Name: Dell XPS 15. Type: laptop. " +
                  "Specifications: processor: Intel Core i7, ram: 16GB, " +
                  "storage: 512GB SSD. Quantity: 3. Value: $1299.99";

List<Float> embedding = embeddingService.generateEmbedding(itemText);
```

## Performance

### Benchmarks (on typical hardware)

| Operation | Time | Hardware |
|-----------|------|----------|
| First embedding | ~100ms | Intel i7, 16GB RAM |
| Subsequent embeddings | ~20-30ms | Intel i7, 16GB RAM |
| Batch (100 items) | ~2-3 seconds | Intel i7, 16GB RAM |

### Memory Usage

- **Model in Memory**: ~150 MB
- **ONNX Runtime**: ~50 MB
- **Total Overhead**: ~200 MB

### Optimization Tips

1. **Batch Processing**: Generate embeddings in batches for better throughput
2. **Caching**: Cache embeddings for frequently accessed items
3. **Async Processing**: Use async methods for non-blocking embedding generation

## Embedding Quality

### Semantic Similarity Examples

The model understands semantic relationships:

```
Similar Items:
- "laptop with 16GB RAM" ↔ "notebook computer 16GB memory" (High similarity)
- "wireless mouse" ↔ "cordless pointing device" (High similarity)
- "mechanical keyboard" ↔ "gaming monitor" (Low similarity)
```

### Use Cases

1. **Product Recommendations**: Find similar products based on descriptions
2. **Search**: Semantic search (not just keyword matching)
3. **Duplicate Detection**: Identify similar/duplicate items
4. **Clustering**: Group similar items together
5. **Classification**: Categorize items based on similarity

## Troubleshooting

### Issue: Model Download Fails

**Problem**: Cannot download model from HuggingFace

**Solutions**:
1. Check internet connectivity
2. Try downloading manually (see Manual Download section)
3. Use a mirror or alternative model URL
4. Check firewall settings

### Issue: Out of Memory

**Problem**: Application crashes with OutOfMemoryError

**Solutions**:
1. Increase JVM heap size: `-Xmx2G`
2. Close unused sessions
3. Process items in smaller batches

### Issue: Slow Initialization

**Problem**: Application takes too long to start

**Solutions**:
1. Pre-download model files
2. Use local file paths instead of URLs
3. Consider using a smaller model

### Issue: Low Accuracy

**Problem**: Embeddings don't capture semantic similarity well

**Solutions**:
1. Ensure text is properly formatted
2. Consider fine-tuning the model on your domain
3. Try a different embedding model (e.g., all-mpnet-base-v2)

## Alternative Models

You can easily switch to different models by changing the URLs:

### Larger Model (Better Quality)
```yaml
embedding:
  model:
    url: https://huggingface.co/sentence-transformers/all-mpnet-base-v2/resolve/main/onnx/model.onnx
  tokenizer:
    url: https://huggingface.co/sentence-transformers/all-mpnet-base-v2/resolve/main/tokenizer.json
  vector:
    size: 768  # Update Qdrant config too
```

### Multilingual Model
```yaml
embedding:
  model:
    url: https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model.onnx
  tokenizer:
    url: https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json
```

## Production Considerations

### High Availability

1. **Pre-warm the Service**: Generate a test embedding on startup
2. **Health Checks**: Verify model is loaded correctly
3. **Fallback**: Implement graceful degradation if model fails

### Monitoring

Monitor these metrics:

- Embedding generation time (P50, P95, P99)
- Memory usage
- Error rates
- Cache hit rates

### Scaling

For high-volume applications:

1. **Multiple Instances**: Run multiple application instances
2. **Dedicated Service**: Extract embedding service into a separate microservice
3. **GPU Acceleration**: Use ONNX Runtime with GPU for faster inference
4. **Model Server**: Use TensorFlow Serving or Triton Inference Server

## Resources

- [Sentence Transformers Documentation](https://www.sbert.net/)
- [ONNX Runtime Documentation](https://onnxruntime.ai/)
- [HuggingFace Model Hub](https://huggingface.co/sentence-transformers)
- [DJL (Deep Java Library)](https://djl.ai/)

## License

- **Model**: Apache 2.0 (Free for commercial use)
- **ONNX Runtime**: MIT License
- **DJL**: Apache 2.0
