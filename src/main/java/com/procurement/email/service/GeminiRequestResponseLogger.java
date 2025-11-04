package com.procurement.email.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Persists Gemini LLM request/response payloads to a dedicated audit log file.
 */
@Component
@Slf4j
public class GeminiRequestResponseLogger {

    private static final int MAX_PAYLOAD_LENGTH = 4000;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectMapper objectMapper;
    private final Path logFilePath;
    private final ReentrantLock fileLock = new ReentrantLock();

    public GeminiRequestResponseLogger(ObjectMapper objectMapper,
                                       @Value("${gemini.audit.log-path:logs/gemini-traffic.log}") String logPath) {
        this.objectMapper = objectMapper;
        this.logFilePath = Paths.get(logPath).toAbsolutePath();
        ensureLogDirectory();
    }

    public void logRequest(String operation, Object payload) {
        writeEntry("REQUEST", operation, payload);
    }

    public void logResponse(String operation, Object payload) {
        writeEntry("RESPONSE", operation, payload);
    }

    private void ensureLogDirectory() {
        try {
            Path directory = logFilePath.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
        } catch (IOException e) {
            log.warn("Unable to create Gemini audit log directory", e);
        }
    }

    private void writeEntry(String entryType, String operation, Object payload) {
        String timestamp = FORMATTER.format(OffsetDateTime.now());
        String serializedPayload = serializePayload(payload);
        StringBuilder builder = new StringBuilder();
        builder.append(timestamp)
                .append(' ')
                .append('[').append(entryType).append(']')
                .append(' ')
                .append(operation)
                .append(System.lineSeparator())
                .append(serializedPayload)
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        fileLock.lock();
        try {
            Files.writeString(logFilePath, builder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write Gemini audit log entry", e);
        } finally {
            fileLock.unlock();
        }
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return "null";
        }

        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return truncate(json);
        } catch (JsonProcessingException e) {
            // Fall back to toString if JSON serialization fails
            String fallback = payload.toString();
            return truncate(fallback);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= MAX_PAYLOAD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_PAYLOAD_LENGTH) + "... (truncated)";
    }
}
