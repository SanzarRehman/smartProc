package com.procurement.email.service;

import com.procurement.email.integration.dto.*;
import com.procurement.email.model.EmailMessage;
import com.procurement.email.model.Item;
import com.procurement.email.model.ProcurementRequest;
import com.procurement.email.model.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for integrating with Gemini AI API for email classification,
 * request extraction, and item recommendation.
 */
@Service
@Slf4j
public class GeminiAIService {

    private final RestTemplate geminiRestTemplate;
    private final String geminiApiUrl;
    private final int maxRetries;

    public GeminiAIService(
            @Qualifier("geminiRestTemplate") RestTemplate geminiRestTemplate,
            @Value("${gemini.api.url}") String geminiApiUrl,
            @Value("${gemini.api.max-retries:3}") int maxRetries) {
        this.geminiRestTemplate = geminiRestTemplate;
        this.geminiApiUrl = geminiApiUrl;
        this.maxRetries = maxRetries;
    }

    /**
     * Validates if a procurement email contains all required information.
     * Uses AI to intelligently check for essential procurement details.
     * Only Item Type and Quantity are strictly required - other fields are optional.
     *
     * @param email the email message to validate
     * @return validation result with missing fields if any
     */
    public EmailProcessingService.TemplateValidationResult validateProcurementEmail(EmailMessage email) {
        log.debug("Validating procurement email template for: {}", email.getFrom());
        
        // Check if this is a reply - if so, include context about it being a follow-up
        String contextNote = "";
        if (email.getInReplyTo() != null && !email.getInReplyTo().isEmpty()) {
            contextNote = "\n\nIMPORTANT: This is a REPLY to a previous email thread. " +
                         "The user may be providing additional information that was requested. " +
                         "Look at the ENTIRE email body including any quoted/previous messages for context.";
        }
        
        String prompt = String.format(
                "You are analyzing a procurement request email. Your job is to determine if the requester has provided " +
                "enough information to process their request for equipment, supplies, or assets.\n\n" +
                "=== EMAIL TO ANALYZE ===\n" +
                "Subject: %s\n\n" +
                "Body:\n%s\n" +
                "=======================%s\n\n" +
                "VALIDATION RULES:\n" +
                "1. REQUIRED (Must have both):\n" +
                "   - Item Type: What is being requested (laptop, monitor, mouse, software, furniture, supplies, etc.)\n" +
                "   - Quantity: How many units (can be a number or words like 'one', 'two', 'a laptop')\n\n" +
                "2. OPTIONAL (Nice to have but not required):\n" +
                "   - Specifications: Technical details, model, features, requirements\n" +
                "   - Reason/Purpose: Why they need it\n" +
                "   - Additional Notes: Urgency, preferences, etc.\n\n" +
                "IMPORTANT GUIDELINES:\n" +
                "- Be FLEXIBLE and INTELLIGENT in parsing - users don't always follow templates exactly\n" +
                "- Look for information in the ENTIRE email including subject line and any quoted text\n" +
                "- Accept natural language (e.g., 'I need a laptop' = Item Type: laptop, Quantity: 1)\n" +
                "- If this is a reply, check if they're providing info that was previously requested\n" +
                "- Only mark as invalid if you truly cannot determine WHAT they want or HOW MANY\n" +
                "- Specifications and Reason are OPTIONAL - don't require them\n\n" +
                "Respond in JSON format:\n" +
                "{\n" +
                "  \"hasAllInfo\": true/false,\n" +
                "  \"missingFields\": [\"Item Type\", \"Quantity\"],\n" +
                "  \"extractedInfo\": {\n" +
                "    \"itemType\": \"extracted value or null\",\n" +
                "    \"quantity\": number or null,\n" +
                "    \"specifications\": \"extracted value or null\",\n" +
                "    \"reason\": \"extracted value or null\"\n" +
                "  },\n" +
                "  \"reasoning\": \"brief explanation of your decision\"\n" +
                "}",
                email.getSubject(),
                email.getBody(),
                contextNote
        );
        
        try {
            Map<String, Object> geminiRequest = buildGeminiRequest(prompt);
            String endpoint = geminiApiUrl + "/models/gemini-2.0-flash-exp:generateContent";
            
            Map<String, Object> response = geminiRestTemplate.postForObject(
                    endpoint, geminiRequest, Map.class);
            
            String text = extractTextFromGeminiResponse(response);
            log.info("Gemini validation response: {}", text);
            
            // Clean up JSON markers
            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
            
            // Parse hasAllInfo
            boolean hasAllInfo = text.contains("\"hasAllInfo\": true") || 
                                text.contains("\"hasAllInfo\":true");
            
            // Extract missing fields from the missingFields array in JSON
            List<String> missingFields = new ArrayList<>();
            if (!hasAllInfo) {
                // Look for the missingFields array in the JSON response
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\"missingFields\"\\s*:\\s*\\[([^\\]]*)\\]");
                java.util.regex.Matcher matcher = pattern.matcher(text);
                
                if (matcher.find()) {
                    String fieldsStr = matcher.group(1);
                    // Extract quoted strings from the array
                    java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
                    java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(fieldsStr);
                    
                    while (fieldMatcher.find()) {
                        String field = fieldMatcher.group(1);
                        // Normalize field names
                        if (field.toLowerCase().contains("item") && field.toLowerCase().contains("type")) {
                            missingFields.add("Item Type");
                        } else if (field.toLowerCase().contains("quantity")) {
                            missingFields.add("Quantity");
                        } else if (field.toLowerCase().contains("spec")) {
                            missingFields.add("Specifications/Requirements");
                        } else if (field.toLowerCase().contains("reason") || field.toLowerCase().contains("purpose")) {
                            missingFields.add("Reason/Purpose");
                        } else {
                            missingFields.add(field);
                        }
                    }
                }
            }
            
            log.info("Validation result: hasAllInfo={}, missingFields={}", hasAllInfo, missingFields);
            
            return new EmailProcessingService.TemplateValidationResult(hasAllInfo, missingFields);
            
        } catch (Exception e) {
            log.error("Failed to validate email with Gemini", e);
            // On error, assume valid to not block the workflow
            return new EmailProcessingService.TemplateValidationResult(true, Collections.emptyList());
        }
    }

    /**
     * Classifies an email to determine if it is procurement-related.
     *
     * @param email the email message to classify
     * @return true if the email is procurement-related, false otherwise
     */
    public boolean isProcurementRelated(EmailMessage email) {
        log.debug("Classifying email from: {}, subject: {}", email.getFrom(), email.getSubject());

        GeminiClassificationRequest request = GeminiClassificationRequest.builder()
                .emailSubject(email.getSubject())
                .emailBody(email.getBody())
                .senderEmail(email.getFrom())
                .build();

        try {
            GeminiClassificationResponse response = executeWithRetry(
                    () -> classifyEmailWithGemini(request),
                    "email classification"
            );

            log.info("Email classification result: isProcurementRelated={}, confidence={}, reasoning={}",
                    response.isProcurementRelated(), response.getConfidenceScore(), response.getReasoning());

            return response.isProcurementRelated();
        } catch (Exception e) {
            log.error("Failed to classify email after {} retries", maxRetries, e);
            // Default to false to avoid processing non-procurement emails
            return false;
        }
    }

    /**
     * Extracts procurement request details from an email.
     *
     * @param email the email message to extract details from
     * @return the extracted procurement request
     */
    public ProcurementRequest extractRequestDetails(EmailMessage email) {
        log.debug("Extracting request details from email: {}", email.getMessageId());

        GeminiExtractionRequest request = GeminiExtractionRequest.builder()
                .emailSubject(email.getSubject())
                .emailBody(email.getBody())
                .senderEmail(email.getFrom())
                .build();

        try {
            GeminiExtractionResponse response = executeWithRetry(
                    () -> extractDetailsWithGemini(request),
                    "request extraction"
            );

            ProcurementRequest procurementRequest = new ProcurementRequest();
            procurementRequest.setRequestId(UUID.randomUUID().toString());
            procurementRequest.setRequesterEmail(email.getFrom());
            procurementRequest.setItemType(response.getItemType());
            procurementRequest.setItemName(response.getItemName());
            procurementRequest.setSpecifications(response.getSpecifications());
            procurementRequest.setQuantity(response.getQuantity());
            procurementRequest.setEstimatedPrice(response.getEstimatedPrice());
            procurementRequest.setAdditionalNotes(response.getAdditionalNotes());
            procurementRequest.setRequestDate(email.getReceivedDate());

            log.info("Extracted procurement request: itemType={}, itemName={}, quantity={}, estimatedPrice={}", 
                    response.getItemType(), response.getItemName(), response.getQuantity(), response.getEstimatedPrice());

            return procurementRequest;
        } catch (Exception e) {
            log.error("Failed to extract request details after {} retries", maxRetries, e);
            throw new AIAnalysisException("Failed to extract procurement request details", e);
        }
    }

    /**
     * Recommends items based on the procurement request, user context, and available items.
     *
     * @param request the procurement request
     * @param userContext the user context including role, designation, and preferences
     * @param availableItems the list of available items from inventory
     * @return the list of recommended items
     */
    public List<Item> recommendItems(
            ProcurementRequest request,
            UserContext userContext,
            List<Item> availableItems) {
        log.debug("Recommending items for request: {}, user: {}, available items: {}",
                request.getRequestId(), userContext.getEmail(), availableItems.size());

        GeminiRecommendationRequest recommendationRequest = GeminiRecommendationRequest.builder()
                .itemType(request.getItemType())
                .requestedSpecifications(request.getSpecifications())
                .quantity(request.getQuantity())
                .userRole(userContext.getRole())
                .userDesignation(userContext.getDesignation())
                .userPreferences(userContext.getPreferences())
                .availableItems(availableItems)
                .build();

        try {
            GeminiRecommendationResponse response = executeWithRetry(
                    () -> recommendItemsWithGemini(recommendationRequest),
                    "item recommendation"
            );

            // Map recommended item IDs back to actual Item objects
            List<Item> recommendedItems = response.getRecommendedItems().stream()
                    .map(recItem -> findItemById(availableItems, recItem.getItemId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            log.info("Recommended {} items based on user role={}, designation={}",
                    recommendedItems.size(), userContext.getRole(), userContext.getDesignation());

            return recommendedItems;
        } catch (Exception e) {
            log.error("Failed to recommend items after {} retries", maxRetries, e);
            throw new AIAnalysisException("Failed to generate item recommendations", e);
        }
    }

    /**
     * Executes an operation with retry logic and exponential backoff.
     */
    private <T> T executeWithRetry(RetryableOperation<T> operation, String operationName) {
        int attempt = 0;
        long backoffMillis = 1000; // Start with 1 second

        while (attempt < maxRetries) {
            try {
                return operation.execute();
            } catch (RestClientException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    log.error("Operation '{}' failed after {} attempts", operationName, maxRetries);
                    throw e;
                }

                log.warn("Operation '{}' failed on attempt {}/{}. Retrying after {}ms...",
                        operationName, attempt, maxRetries, backoffMillis);

                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIAnalysisException("Retry interrupted", ie);
                }

                // Exponential backoff
                backoffMillis *= 2;
            }
        }

        throw new AIAnalysisException("Should not reach here");
    }

    /**
     * Calls Gemini API for email classification.
     */
    private GeminiClassificationResponse classifyEmailWithGemini(GeminiClassificationRequest request) {
        // Build prompt for Gemini
        String prompt = buildClassificationPrompt(request);
        Map<String, Object> geminiRequest = buildGeminiRequest(prompt);

        String endpoint = geminiApiUrl + "/models/gemini-2.0-flash-exp:generateContent";
        
        try {
            Map<String, Object> response = geminiRestTemplate.postForObject(
                    endpoint, geminiRequest, Map.class);
            
            return parseClassificationResponse(response);
        } catch (RestClientException e) {
            log.error("Gemini API call failed for classification", e);
            throw e;
        }
    }

    /**
     * Calls Gemini API for request extraction.
     */
    private GeminiExtractionResponse extractDetailsWithGemini(GeminiExtractionRequest request) {
        String prompt = buildExtractionPrompt(request);
        Map<String, Object> geminiRequest = buildGeminiRequest(prompt);

        String endpoint = geminiApiUrl + "/models/gemini-2.0-flash-exp:generateContent";
        
        try {
            Map<String, Object> response = geminiRestTemplate.postForObject(
                    endpoint, geminiRequest, Map.class);
            
            return parseExtractionResponse(response);
        } catch (RestClientException e) {
            log.error("Gemini API call failed for extraction", e);
            throw e;
        }
    }

    /**
     * Calls Gemini API for item recommendation.
     */
    private GeminiRecommendationResponse recommendItemsWithGemini(GeminiRecommendationRequest request) {
        String prompt = buildRecommendationPrompt(request);
        Map<String, Object> geminiRequest = buildGeminiRequest(prompt);

        String endpoint = geminiApiUrl + "/models/gemini-2.0-flash-exp:generateContent";
        
        try {
            Map<String, Object> response = geminiRestTemplate.postForObject(
                    endpoint, geminiRequest, Map.class);
            
            return parseRecommendationResponse(response, request.getAvailableItems());
        } catch (RestClientException e) {
            log.error("Gemini API call failed for recommendation", e);
            throw e;
        }
    }

    private String buildClassificationPrompt(GeminiClassificationRequest request) {
        return String.format(
                "Analyze the following email and determine if it is a procurement request for equipment, supplies, or assets.\n\n" +
                "Subject: %s\n" +
                "Body: %s\n\n" +
                "Respond in JSON format with:\n" +
                "{\n" +
                "  \"isProcurementRelated\": true/false,\n" +
                "  \"reasoning\": \"brief explanation\",\n" +
                "  \"confidenceScore\": 0.0-1.0\n" +
                "}",
                request.getEmailSubject(),
                request.getEmailBody()
        );
    }

    private String buildExtractionPrompt(GeminiExtractionRequest request) {
        return String.format(
                "Extract procurement request details from the following email.\n\n" +
                "Subject: %s\n" +
                "Body: %s\n\n" +
                "IMPORTANT INSTRUCTIONS:\n" +
                "1. Extract the SPECIFIC item name if mentioned (e.g., 'MacBook Pro', 'Dell XPS 15', 'iPhone 15')\n" +
                "2. If only a generic type is mentioned, use that (e.g., 'laptop', 'monitor')\n" +
                "3. Extract ALL specifications mentioned (brand, model, RAM, storage, screen size, etc.)\n" +
                "4. Include any urgency or special requirements in additionalNotes\n" +
                "5. Estimate a reasonable price based on the item and specifications\n\n" +
                "Respond in JSON format with:\n" +
                "{\n" +
                "  \"itemType\": \"laptop/monitor/accessory/software/furniture/supplies/etc\",\n" +
                "  \"itemName\": \"specific item name if mentioned, otherwise same as itemType\",\n" +
                "  \"specifications\": {\n" +
                "    \"brand\": \"if mentioned\",\n" +
                "    \"model\": \"if mentioned\",\n" +
                "    \"ram\": \"if mentioned\",\n" +
                "    \"storage\": \"if mentioned\",\n" +
                "    \"other_specs\": \"any other details\"\n" +
                "  },\n" +
                "  \"quantity\": number,\n" +
                "  \"estimatedPrice\": number (estimated unit price in USD),\n" +
                "  \"additionalNotes\": \"urgency, purpose, preferences, etc.\"\n" +
                "}",
                request.getEmailSubject(),
                request.getEmailBody()
        );
    }

    private String buildRecommendationPrompt(GeminiRecommendationRequest request) {
        StringBuilder itemsList = new StringBuilder();
        for (Item item : request.getAvailableItems()) {
            itemsList.append(String.format("- ID: %s, Name: %s, Type: %s, Specs: %s, Available: %d\n",
                    item.getId(), item.getName(), item.getType(),
                    item.getSpecifications(), item.getAvailableQuantity()));
        }

        return String.format(
                "Recommend items from the available inventory based on the following:\n\n" +
                "Request:\n" +
                "- Item Type: %s\n" +
                "- Requested Specs: %s\n" +
                "- Quantity: %d\n\n" +
                "User Context:\n" +
                "- Role: %s\n" +
                "- Designation: %s\n" +
                "- Preferences: %s\n\n" +
                "Available Items:\n%s\n" +
                "Respond in JSON format with:\n" +
                "{\n" +
                "  \"recommendedItems\": [\n" +
                "    {\"itemId\": \"id\", \"itemName\": \"name\", \"matchScore\": 0.0-1.0, \"matchReason\": \"why this item\"}\n" +
                "  ],\n" +
                "  \"reasoning\": \"overall recommendation reasoning\"\n" +
                "}",
                request.getItemType(),
                request.getRequestedSpecifications(),
                request.getQuantity(),
                request.getUserRole(),
                request.getUserDesignation(),
                request.getUserPreferences(),
                itemsList.toString()
        );
    }

    private Map<String, Object> buildGeminiRequest(String prompt) {
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", Collections.singletonList(part));

        Map<String, Object> request = new HashMap<>();
        request.put("contents", Collections.singletonList(content));

        return request;
    }

    @SuppressWarnings("unchecked")
    private GeminiClassificationResponse parseClassificationResponse(Map<String, Object> response) {
        try {
            String text = extractTextFromGeminiResponse(response);
            // Parse JSON from text
            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            // Simple JSON parsing (in production, use Jackson or Gson)
            boolean isProcurement = text.contains("\"isProcurementRelated\": true") || 
                                   text.contains("\"isProcurementRelated\":true");
            
            return GeminiClassificationResponse.builder()
                    .isProcurementRelated(isProcurement)
                    .reasoning("Parsed from Gemini response")
                    .confidenceScore(0.85)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse classification response", e);
            return GeminiClassificationResponse.builder()
                    .isProcurementRelated(false)
                    .reasoning("Parse error")
                    .confidenceScore(0.0)
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private GeminiExtractionResponse parseExtractionResponse(Map<String, Object> response) {
        try {
            String text = extractTextFromGeminiResponse(response);
            log.info("Gemini extraction response: {}", text);
            
            // Clean up JSON markers
            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
            
            // Extract item type
            String itemType = "laptop"; // default
            java.util.regex.Pattern typePattern = java.util.regex.Pattern.compile("\"itemType\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher typeMatcher = typePattern.matcher(text);
            if (typeMatcher.find()) {
                itemType = typeMatcher.group(1).toLowerCase();
            }
            
            // Extract item name (specific name like "MacBook Pro")
            String itemName = itemType; // default to itemType
            java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile("\"itemName\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher nameMatcher = namePattern.matcher(text);
            if (nameMatcher.find()) {
                itemName = nameMatcher.group(1);
            }
            
            // Extract quantity
            int quantity = 1; // default
            java.util.regex.Pattern qtyPattern = java.util.regex.Pattern.compile("\"quantity\"\\s*:\\s*(\\d+)");
            java.util.regex.Matcher qtyMatcher = qtyPattern.matcher(text);
            if (qtyMatcher.find()) {
                quantity = Integer.parseInt(qtyMatcher.group(1));
            }
            
            // Extract estimated price
            java.math.BigDecimal estimatedPrice = java.math.BigDecimal.valueOf(1000); // default
            java.util.regex.Pattern pricePattern = java.util.regex.Pattern.compile("\"estimatedPrice\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
            java.util.regex.Matcher priceMatcher = pricePattern.matcher(text);
            if (priceMatcher.find()) {
                estimatedPrice = new java.math.BigDecimal(priceMatcher.group(1));
            }
            
            // Extract specifications (try to parse the specifications object)
            Map<String, String> specifications = new java.util.HashMap<>();
            specifications.put("type", itemType);
            
            // Look for specifications object in JSON
            java.util.regex.Pattern specsPattern = java.util.regex.Pattern.compile(
                "\"specifications\"\\s*:\\s*\\{([^}]+)\\}");
            java.util.regex.Matcher specsMatcher = specsPattern.matcher(text);
            if (specsMatcher.find()) {
                String specsContent = specsMatcher.group(1);
                // Extract key-value pairs from specifications
                java.util.regex.Pattern kvPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher kvMatcher = kvPattern.matcher(specsContent);
                while (kvMatcher.find()) {
                    String key = kvMatcher.group(1);
                    String value = kvMatcher.group(2);
                    if (value != null && !value.equals("null") && !value.isEmpty()) {
                        specifications.put(key, value);
                    }
                }
            }
            
            // Extract additional notes
            String additionalNotes = "Extracted from email using Gemini AI";
            java.util.regex.Pattern notesPattern = java.util.regex.Pattern.compile("\"additionalNotes\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher notesMatcher = notesPattern.matcher(text);
            if (notesMatcher.find()) {
                additionalNotes = notesMatcher.group(1);
            }
            
            log.info("Extracted: itemType={}, itemName={}, quantity={}, estimatedPrice={}", 
                    itemType, itemName, quantity, estimatedPrice);
            
            return GeminiExtractionResponse.builder()
                    .itemType(itemType)
                    .itemName(itemName)
                    .specifications(specifications)
                    .quantity(quantity)
                    .estimatedPrice(estimatedPrice)
                    .additionalNotes(additionalNotes)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse extraction response", e);
            throw new AIAnalysisException("Failed to parse extraction response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private GeminiRecommendationResponse parseRecommendationResponse(
            Map<String, Object> response, List<Item> availableItems) {
        try {
            String text = extractTextFromGeminiResponse(response);
            log.info("Gemini recommendation response: {}", text);
            
            // Clean up JSON markers
            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
            
            // Parse the JSON response to get recommended item IDs
            List<GeminiRecommendationResponse.RecommendedItem> recommended = new ArrayList<>();
            
            // Extract item IDs from the response
            // Look for "itemId": "xxx" patterns
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"itemId\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                String itemId = matcher.group(1);
                // Find the matching item
                Optional<Item> matchedItem = availableItems.stream()
                        .filter(item -> item.getId().equals(itemId))
                        .findFirst();
                
                if (matchedItem.isPresent()) {
                    Item item = matchedItem.get();
                    recommended.add(GeminiRecommendationResponse.RecommendedItem.builder()
                            .itemId(item.getId())
                            .itemName(item.getName())
                            .matchScore(0.9)
                            .matchReason("Best match based on specifications and user role")
                            .build());
                    log.info("Matched item: {} ({})", item.getName(), item.getId());
                }
            }
            
            // If no items matched from AI response, return empty list
            if (recommended.isEmpty()) {
                log.warn("No items matched from Gemini response, returning empty list");
            }
            
            return GeminiRecommendationResponse.builder()
                    .recommendedItems(recommended)
                    .reasoning("Recommended based on user context and specifications")
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse recommendation response", e);
            throw new AIAnalysisException("Failed to parse recommendation response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<String, Object> response) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new AIAnalysisException("No candidates in Gemini response");
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        
        if (parts == null || parts.isEmpty()) {
            throw new AIAnalysisException("No parts in Gemini response");
        }

        return (String) parts.get(0).get("text");
    }

    private Optional<Item> findItemById(List<Item> items, String itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();
    }

    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute();
    }

    /**
     * Exception thrown when AI analysis fails.
     */
    public static class AIAnalysisException extends RuntimeException {
        public AIAnalysisException(String message) {
            super(message);
        }

        public AIAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
