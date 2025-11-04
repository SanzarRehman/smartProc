package com.procurement.email.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Qdrant vector database client.
 * Sets up the connection to Qdrant running in Docker.
 */
@Configuration
@Slf4j
public class QdrantConfig {

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.collection.name:inventory_items}")
    private String collectionName;

    @Value("${qdrant.use-tls:false}")
    private boolean useTls;

    /**
     * Creates and configures a Qdrant client bean.
     * 
     * @return Configured QdrantClient instance
     */
    @Bean
    public QdrantClient qdrantClient() {
        log.info("Initializing Qdrant client connecting to {}:{}", qdrantHost, qdrantPort);
        
        QdrantClient client = new QdrantClient(
            QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, useTls)
                .build()
        );
        
        log.info("Qdrant client initialized successfully. Collection: {}", collectionName);
        return client;
    }

    /**
     * Gets the collection name for inventory items.
     * 
     * @return Collection name
     */
    @Bean
    public String qdrantCollectionName() {
        return collectionName;
    }
}
