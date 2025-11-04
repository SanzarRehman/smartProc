package com.procurement.email.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Service for generating text embeddings using Sentence Transformers (all-MiniLM-L6-v2).
 * Uses ONNX Runtime for efficient inference and HuggingFace tokenizers.
 * 
 * Model: sentence-transformers/all-MiniLM-L6-v2
 * - Embedding size: 384 dimensions
 * - Fast and efficient for semantic similarity tasks
 * - Open source and free to use
 */
@Service
@Slf4j
public class EmbeddingService {

    private static final int EMBEDDING_SIZE = 384;
    private static final int MAX_LENGTH = 256;
    
    @Value("${embedding.model.url:https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx}")
    private String modelUrl;
    
    @Value("${embedding.tokenizer.url:https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json}")
    private String tokenizerUrl;

    
    @Value("${embedding.cache.dir:${java.io.tmpdir}/embedding-models}")
    private String cacheDir;

    private OrtEnvironment environment;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;

    /**
     * Initializes the embedding model and tokenizer.
     * Downloads the model files if not already cached.
     */
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Embedding Service with model: all-MiniLM-L6-v2");
            
            // Create cache directory
            Path cachePath = Path.of(cacheDir);
            Files.createDirectories(cachePath);
            
            // Download or load model
            Path modelPath = cachePath.resolve("model.onnx");
            if (!Files.exists(modelPath)) {
                log.info("Downloading ONNX model from {}", modelUrl);
                downloadFile(modelUrl, modelPath);
                log.info("Model downloaded successfully");
            } else {
                log.info("Using cached model from {}", modelPath);
            }
            
            // Download or load tokenizer
            Path tokenizerPath = cachePath.resolve("tokenizer.json");
            if (!Files.exists(tokenizerPath)) {
                log.info("Downloading tokenizer from {}", tokenizerUrl);
                downloadFile(tokenizerUrl, tokenizerPath);
                log.info("Tokenizer downloaded successfully");
            } else {
                log.info("Using cached tokenizer from {}", tokenizerPath);
            }
            
            // Initialize ONNX Runtime
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
            session = environment.createSession(modelPath.toString(), sessionOptions);
            
            log.info("ONNX Runtime session created successfully");
            
            // Initialize tokenizer
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath, Map.of(
                    "padding", "max_length",
                    "maxLength", String.valueOf(MAX_LENGTH),
                    "truncation", "true"
            ));
            
            log.info("Embedding Service initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Embedding Service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize embedding service", e);
        }
    }

    /**
     * Downloads a file from a URL to a local path.
     * 
     * @param urlString URL to download from
     * @param destination Destination path
     */
    private void downloadFile(String urlString, Path destination) throws IOException {
        URL url = new URL(urlString);
        Files.copy(url.openStream(), destination, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Generates an embedding vector from text using the Sentence Transformer model.
     * 
     * @param text Input text to embed
     * @return Embedding vector as a list of floats (384 dimensions)
     */
    public List<Float> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Attempted to generate embedding for null or empty text");
            return createZeroVector();
        }

        try {
            // Tokenize the input text
            Encoding encoding = tokenizer.encode(text);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            long[] tokenTypeIds = encoding.getTypeIds();
            
            // Prepare input tensors
            long[][] inputIdsBatch = new long[][]{inputIds};
            long[][] attentionMaskBatch = new long[][]{attentionMask};
            long[][] tokenTypeIdsBatch = new long[][]{tokenTypeIds};
            
            // Create ONNX tensors
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, inputIdsBatch);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, attentionMaskBatch);
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, tokenTypeIdsBatch);
            
            // Run inference with all required inputs
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);
            
            OrtSession.Result results = session.run(inputs);
            
            // Extract embeddings (last_hidden_state)
            float[][][] output = (float[][][]) results.get(0).getValue();
            
            // Mean pooling: average the token embeddings
            float[] embedding = meanPooling(output[0], attentionMask);
            
            // Normalize the embedding
            float[] normalized = normalizeVector(embedding);
            
            // Convert to List<Float>
            List<Float> embeddingList = new ArrayList<>(EMBEDDING_SIZE);
            for (float value : normalized) {
                embeddingList.add(value);
            }
            
            // Clean up
            inputIdsTensor.close();
            attentionMaskTensor.close();
            tokenTypeIdsTensor.close();
            results.close();
            
            return embeddingList;
            
        } catch (OrtException e) {
            log.error("Error generating embedding with ONNX Runtime: {}", e.getMessage(), e);
            return createZeroVector();
        }
    }

    /**
     * Performs mean pooling on token embeddings with attention mask.
     * 
     * @param tokenEmbeddings Token embeddings from the model
     * @param attentionMask Attention mask for valid tokens
     * @return Pooled embedding vector
     */
    private float[] meanPooling(float[][] tokenEmbeddings, long[] attentionMask) {
        float[] pooled = new float[EMBEDDING_SIZE];
        int validTokens = 0;
        
        for (int i = 0; i < attentionMask.length; i++) {
            if (attentionMask[i] == 1) {
                validTokens++;
                for (int j = 0; j < EMBEDDING_SIZE; j++) {
                    pooled[j] += tokenEmbeddings[i][j];
                }
            }
        }
        
        // Average by number of valid tokens
        if (validTokens > 0) {
            for (int j = 0; j < EMBEDDING_SIZE; j++) {
                pooled[j] /= validTokens;
            }
        }
        
        return pooled;
    }

    /**
     * Normalizes a vector using L2 normalization.
     * 
     * @param vector Input vector
     * @return Normalized vector
     */
    private float[] normalizeVector(float[] vector) {
        // Calculate L2 norm
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        
        // Avoid division by zero
        if (norm < 1e-10) {
            return vector;
        }
        
        // Normalize
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        
        return normalized;
    }

    /**
     * Creates a zero vector (fallback).
     * 
     * @return Vector of zeros
     */
    private List<Float> createZeroVector() {
        List<Float> vector = new ArrayList<>(EMBEDDING_SIZE);
        for (int i = 0; i < EMBEDDING_SIZE; i++) {
            vector.add(0.0f);
        }
        return vector;
    }

    /**
     * Gets the embedding size.
     * 
     * @return Embedding dimension size (384)
     */
    public int getEmbeddingSize() {
        return EMBEDDING_SIZE;
    }

    /**
     * Clean up resources on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        try {
            if (session != null) {
                session.close();
                log.info("ONNX Runtime session closed");
            }
            if (environment != null) {
                environment.close();
                log.info("ONNX Runtime environment closed");
            }
        } catch (Exception e) {
            log.error("Error cleaning up embedding service: {}", e.getMessage(), e);
        }
    }
}
