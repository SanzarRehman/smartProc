package com.procurement.email.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.email.integration.dto.*; // Importing necessary DTOs
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
    private final String openRouterModel;
    private final int maxRetries;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final GeminiRequestResponseLogger requestResponseLogger;
    private static final Map<String, Integer> NUMBER_WORDS;
    private static final java.util.regex.Pattern NUMBER_WORD_PATTERN;
    private static final java.util.regex.Pattern DIGIT_QUANTITY_PATTERN = java.util.regex.Pattern.compile("\\b([1-9][0-9]*)\\b");
    private static final Map<String, List<String>> ITEM_TYPE_KEYWORDS;
    private static final int MAX_INVENTORY_MATCH_ITEMS = 25;

    static {
        Map<String, Integer> words = new LinkedHashMap<>();
        words.put("one", 1);
        words.put("two", 2);
        words.put("three", 3);
        words.put("four", 4);
        words.put("five", 5);
        words.put("six", 6);
        words.put("seven", 7);
        words.put("eight", 8);
        words.put("nine", 9);
        words.put("ten", 10);
        words.put("eleven", 11);
        words.put("twelve", 12);
        words.put("dozen", 12);
        words.put("pair", 2);
        words.put("couple", 2);
        NUMBER_WORDS = Collections.unmodifiableMap(words);
        String pattern = NUMBER_WORDS.keySet().stream()
                .map(java.util.regex.Pattern::quote)
                .collect(Collectors.joining("|"));
        NUMBER_WORD_PATTERN = java.util.regex.Pattern.compile("\\b(" + pattern + ")\\b");

    Map<String, List<String>> keywordMap = new LinkedHashMap<>();
    keywordMap.put("laptop", List.of("laptop", "macbook", "notebook", "thinkpad", "xps", "mac book"));
    keywordMap.put("monitor", List.of("monitor", "display", "screen", "ultrasharp", "4k"));
    keywordMap.put("mouse", List.of("mouse", "mice", "trackpad", "pointer"));
    keywordMap.put("keyboard", List.of("keyboard", "key board", "keypad", "mx keys"));
    keywordMap.put("headset", List.of("headset", "headphones", "earbuds", "ear phones"));
    keywordMap.put("accessory", List.of("accessory", "peripheral"));
    keywordMap.put("software", List.of("software", "license", "subscription"));
    ITEM_TYPE_KEYWORDS = Collections.unmodifiableMap(keywordMap);
    }

    public GeminiAIService(
            @Qualifier("openRouterRestTemplate") RestTemplate openRouterRestTemplate,
            @Value("${openrouter.api.url}") String openRouterApiUrl,
            @Value("${openrouter.api.model}") String openRouterModel,
            @Value("${openrouter.api.max-retries:3}") int maxRetries,
            GeminiRequestResponseLogger requestResponseLogger) {
        this.geminiRestTemplate = openRouterRestTemplate;
        this.geminiApiUrl = openRouterApiUrl;
        this.openRouterModel = openRouterModel;
        this.maxRetries = maxRetries;
        this.requestResponseLogger = requestResponseLogger;
    }

    /**
     * Determines if an email is a procurement request, a follow-up reply, or unrelated.
     * Also evaluates whether the email contains sufficient details to proceed.
     */
    public GeminiEmailIntentResponse analyzeEmailIntent(EmailMessage email) {
        log.debug("Analyzing email intent for message: {}", email.getMessageId());

        GeminiEmailIntentRequest request = GeminiEmailIntentRequest.builder()
                .senderEmail(email.getFrom())
                .emailSubject(email.getSubject())
                .emailBody(email.getBody())
                .inReplyToMessageId(email.getInReplyTo())
                .build();

        try {
            GeminiEmailIntentResponse response = executeWithRetry(
                    () -> determineEmailIntentWithGemini(request),
                    "email intent analysis"
            );

            log.info("Email intent classified as {} (procurementRelated={}, hasAllDetails={}, reply={})",
                    response.getIntentType(),
                    response.isProcurementRelated(),
                    response.isHasAllDetails(),
                    response.isReply());

            return response;
        } catch (Exception e) {
            log.error("Failed to analyze email intent after {} retries", maxRetries, e);
            return GeminiEmailIntentResponse.builder()
                    .procurementRelated(false)
                    .reply(false)
                    .hasAllDetails(false)
                    .intentType(GeminiEmailIntentResponse.IntentType.NOT_PROCUREMENT)
                    .reasoning("Fallback due to analysis error")
                    .build();
        }
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
        Map<String, Object> response = postToGemini("validateProcurementEmail", geminiRequest);
            
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
            
            OptionalInt explicitQuantity = detectExplicitQuantity(email.getSubject(), email.getBody());
            if (explicitQuantity.isEmpty()) {
                if (missingFields.stream().noneMatch(field -> field.equalsIgnoreCase("Quantity"))) {
                    missingFields.add("Quantity");
                }
                hasAllInfo = false;
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
            int quantity = response.getQuantity();
            OptionalInt explicitQuantity = detectExplicitQuantity(email.getSubject(), email.getBody());
            if (explicitQuantity.isPresent()) {
                quantity = explicitQuantity.getAsInt();
            } else {
                if (quantity > 1) {
                    log.debug("Gemini suggested quantity {} without explicit mention; defaulting to 1", quantity);
                    quantity = 1;
                } else if (quantity <= 0) {
                    quantity = 1;
                }
            }
            procurementRequest.setQuantity(quantity);
            procurementRequest.setEstimatedPrice(response.getEstimatedPrice());
            procurementRequest.setAdditionalNotes(response.getAdditionalNotes());
            procurementRequest.setRequestDate(email.getReceivedDate());
            procurementRequest.setCandidateItemIds(Collections.emptyList());

            normalizeExtractedItemDetails(procurementRequest, email);

            log.info("Extracted procurement request: itemType={}, itemName={}, quantity={}, estimatedPrice={}", 
                    response.getItemType(), response.getItemName(), response.getQuantity(), response.getEstimatedPrice());

            return procurementRequest;
        } catch (Exception e) {
            log.error("Failed to extract request details after {} retries", maxRetries, e);
            throw new AIAnalysisException("Failed to extract procurement request details", e);
        }
    }

    /**
     * Parses a confirmation email to determine selected inventory items.
     *
     * @param request the confirmation parsing request payload
     * @return the parsed confirmation response
     */
    public GeminiConfirmationResponse parseConfirmationEmail(GeminiConfirmationRequest request) {
        log.debug("Parsing confirmation email for sender: {}", request.getSenderEmail());

        try {
            GeminiConfirmationResponse response = executeWithRetry(
                    () -> parseConfirmationWithGemini(request),
                    "confirmation parsing"
            );

            if (response.getSelectedItemIds() == null) {
                response.setSelectedItemIds(Collections.emptyList());
            }

            return response;
        } catch (Exception e) {
            log.error("Failed to parse confirmation email after {} retries", maxRetries, e);
            return GeminiConfirmationResponse.builder()
                    .confirmed(false)
                    .selectedItemIds(Collections.emptyList())
                    .reasoning("Fallback due to Gemini error")
                    .build();
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

    public GeminiInventoryMatchResponse matchInventoryItems(ProcurementRequest request, List<Item> inventoryItems) {
    if (inventoryItems == null || inventoryItems.isEmpty()) {
        return GeminiInventoryMatchResponse.builder()
            .matchingItemIds(Collections.emptyList())
            .reasoning("No inventory available")
            .build();
    }

    try {
        return executeWithRetry(
            () -> matchInventoryItemsWithGemini(request, inventoryItems),
            "inventory matching"
        );
    } catch (Exception e) {
        log.error("Failed to match inventory items after {} retries", maxRetries, e);
        return GeminiInventoryMatchResponse.builder()
            .matchingItemIds(Collections.emptyList())
            .reasoning("Match error")
            .build();
    }
    }

    private GeminiInventoryMatchResponse matchInventoryItemsWithGemini(ProcurementRequest request, List<Item> inventoryItems) {
    String normalizedType = Optional.ofNullable(request.getItemType())
        .map(value -> value.toLowerCase(Locale.ROOT))
        .orElse(null);

    List<Item> prioritized = inventoryItems.stream()
        .filter(Objects::nonNull)
        .sorted((a, b) -> {
            boolean aMatch = itemTypeMatches(normalizedType, a.getType());
            boolean bMatch = itemTypeMatches(normalizedType, b.getType());
            if (aMatch == bMatch) {
            return 0;
            }
            return aMatch ? -1 : 1;
        })
        .collect(Collectors.toList());

    List<GeminiInventoryMatchRequest.InventoryItemSummary> summaries = prioritized.stream()
        .limit(MAX_INVENTORY_MATCH_ITEMS)
        .map(item -> GeminiInventoryMatchRequest.InventoryItemSummary.builder()
            .itemId(item.getId())
            .itemName(truncate(item.getName(), 80))
            .itemType(item.getType())
            .availableQuantity(item.getAvailableQuantity())
            .build())
        .collect(Collectors.toList());

    GeminiInventoryMatchRequest matchRequest = GeminiInventoryMatchRequest.builder()
        .itemType(request.getItemType())
        .itemName(request.getItemName())
        .quantity(request.getQuantity())
        .specifications(request.getSpecifications())
        .inventoryItems(summaries)
        .build();

    String prompt = buildInventoryMatchPrompt(matchRequest);
    Map<String, Object> geminiRequest = buildGeminiRequest(prompt);

    Map<String, Object> response = postToGemini("matchInventoryItems", geminiRequest);
    return parseInventoryMatchResponse(response);
    }

    private boolean itemTypeMatches(String normalizedType, String candidateType) {
        if (normalizedType == null || candidateType == null) {
            return false;
        }

        String lowerCandidate = candidateType.toLowerCase(Locale.ROOT);
        if (lowerCandidate.equals(normalizedType)) {
            return true;
        }

        List<String> synonyms = ITEM_TYPE_KEYWORDS.get(normalizedType);
        if (synonyms != null) {
            for (String synonym : synonyms) {
                if (lowerCandidate.contains(synonym)) {
                    return true;
                }
            }
        }

        return lowerCandidate.contains(normalizedType);
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

        try {
            Map<String, Object> response = postToGemini("classifyEmail", geminiRequest);
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

        try {
            Map<String, Object> response = postToGemini("extractDetails", geminiRequest);
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

        try {
            Map<String, Object> response = postToGemini("recommendItems", geminiRequest);
            return parseRecommendationResponse(response, request.getAvailableItems());
        } catch (RestClientException e) {
            log.error("Gemini API call failed for recommendation", e);
            throw e;
        }
    }

    private GeminiConfirmationResponse parseConfirmationWithGemini(GeminiConfirmationRequest request) {
        String prompt = buildConfirmationPrompt(request);
        Map<String, Object> geminiRequest = buildGeminiRequest(prompt);

        try {
            Map<String, Object> response = postToGemini("parseConfirmation", geminiRequest);
            return parseConfirmationResponse(response);
        } catch (RestClientException e) {
            log.error("Gemini API call failed for confirmation parsing", e);
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
        List<Item> availableItems = request.getAvailableItems() == null
                ? Collections.emptyList()
                : request.getAvailableItems();

        StringBuilder itemsList = new StringBuilder();
        List<Item> nonNullItems = availableItems.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (nonNullItems.isEmpty()) {
            itemsList.append("- No inventory matches were found.\n");
        } else {
            nonNullItems.forEach(item -> itemsList.append(formatItemForPrompt(item)).append('\n'));
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

    private String formatItemForPrompt(Item item) {
        StringBuilder builder = new StringBuilder();
        builder.append("- ID: ").append(item.getId())
                .append(", Name: ").append(truncate(item.getName(), 80));
        if (item.getType() != null) {
            builder.append(", Type: ").append(item.getType());
        }
        builder.append(", Available: ").append(item.getAvailableQuantity());
        return builder.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private OptionalInt detectExplicitQuantity(String... texts) {
        if (texts == null || texts.length == 0) {
            return OptionalInt.empty();
        }

        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }

            java.util.regex.Matcher digitMatcher = DIGIT_QUANTITY_PATTERN.matcher(text);
            if (digitMatcher.find()) {
                try {
                    return OptionalInt.of(Integer.parseInt(digitMatcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Continue to word-based detection if parsing fails
                }
            }

            String lower = text.toLowerCase(Locale.ROOT);
            java.util.regex.Matcher wordMatcher = NUMBER_WORD_PATTERN.matcher(lower);
            if (wordMatcher.find()) {
                Integer value = NUMBER_WORDS.get(wordMatcher.group(1));
                if (value != null) {
                    return OptionalInt.of(value);
                }
            }
        }

        return OptionalInt.empty();
    }

    private void normalizeExtractedItemDetails(ProcurementRequest request, EmailMessage email) {
        Optional<String> inferredType = detectItemType(email.getSubject(), email.getBody());
        inferredType.ifPresent(type -> {
            request.setItemType(type);
            Map<String, String> specs = request.getSpecifications();
            if (specs == null) {
                specs = new HashMap<>();
            }
            specs.putIfAbsent("type", type);
            request.setSpecifications(specs);

            if (request.getItemName() == null || request.getItemName().isBlank() || !textContainsKeyword(request.getItemName(), ITEM_TYPE_KEYWORDS.get(type))) {
                request.setItemName(capitalize(type));
            }
        });
    }

    private Optional<String> detectItemType(String... texts) {
        if (texts == null) {
            return Optional.empty();
        }

        Map<String, Integer> hitCount = new HashMap<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> entry : ITEM_TYPE_KEYWORDS.entrySet()) {
                String type = entry.getKey();
                for (String keyword : entry.getValue()) {
                    if (lower.contains(keyword)) {
                        hitCount.merge(type, 1, Integer::sum);
                    }
                }
            }
        }

        return hitCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    private boolean textContainsKeyword(String text, List<String> keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(lower::contains);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private GeminiEmailIntentResponse determineEmailIntentWithGemini(GeminiEmailIntentRequest request) {
        String prompt = buildIntentPrompt(request);
        Map<String, Object> geminiRequest = buildGeminiRequest(prompt);

        Map<String, Object> response = postToGemini("analyzeEmailIntent", geminiRequest);
        return parseIntentResponse(response);
    }

    private String buildIntentPrompt(GeminiEmailIntentRequest request) {
        String replyContext = request.getInReplyToMessageId() != null && !request.getInReplyToMessageId().isEmpty()
                ? "This email is part of an existing thread (In-Reply-To header present)." :
                "This email appears to start a new thread (no In-Reply-To header).";

        return String.format(
                "You are an assistant in charge of routing procurement-related emails.\n" +
                "%s\n" +
                "Classify the intent of the email and whether it contains enough information to process a procurement request.\n" +
                "Focus on: item type, quantity, specifications, and whether the sender is confirming a recommendation.\n\n" +
                "EMAIL DETAILS:\n" +
                "From: %s\n" +
                "Subject: %s\n" +
                "Body:\n%s\n\n" +
                "Respond in JSON with the following structure:\n" +
                "{\n" +
                "  \"isProcurementRelated\": true|false,\n" +
                "  \"intentType\": \"NEW_REQUEST_WITH_DETAILS|NEW_REQUEST_MISSING_DETAILS|REPLY_CONFIRMATION|REPLY_INFORMATION|NOT_PROCUREMENT\",\n" +
                "  \"hasAllDetails\": true|false,\n" +
                "  \"isReply\": true|false,\n" +
                "  \"missingDetails\": [\"Quantity\", \"Item Type\", ...],\n" +
                "  \"reasoning\": \"short explanation\"\n" +
                "}\n" +
                "If the email confirms or approves a recommended item, set intentType to REPLY_CONFIRMATION even if the word 'confirm' isn't present explicitly.\n" +
                "If the sender is providing additional information but not approving a purchase, use REPLY_INFORMATION.\n",
                replyContext,
                request.getSenderEmail(),
                request.getEmailSubject(),
                request.getEmailBody()
        );
    }

    private GeminiEmailIntentResponse parseIntentResponse(Map<String, Object> response) {
        try {
            String text = extractTextFromGeminiResponse(response);
            log.info("Gemini intent response: {}", text);

            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            JsonNode root = OBJECT_MAPPER.readTree(text);

            boolean isProcurement = root.path("isProcurementRelated").asBoolean(false);
            boolean hasAllDetails = root.path("hasAllDetails").asBoolean(false);
            boolean isReply = root.path("isReply").asBoolean(false);

            String intentValue = root.path("intentType").asText("NOT_PROCUREMENT").toUpperCase(Locale.ROOT);
            GeminiEmailIntentResponse.IntentType intentType;
            try {
                intentType = GeminiEmailIntentResponse.IntentType.valueOf(intentValue);
            } catch (IllegalArgumentException ex) {
                intentType = GeminiEmailIntentResponse.IntentType.NOT_PROCUREMENT;
            }

            List<String> missing = new ArrayList<>();
            JsonNode missingNode = root.get("missingDetails");
            if (missingNode != null && missingNode.isArray()) {
                missingNode.forEach(node -> {
                    if (node.isTextual()) {
                        missing.add(node.asText());
                    }
                });
            }

            String reasoning = null;
            if (root.has("reasoning") && root.get("reasoning").isTextual()) {
                reasoning = root.get("reasoning").asText();
            }

            return GeminiEmailIntentResponse.builder()
                    .procurementRelated(isProcurement)
                    .hasAllDetails(hasAllDetails)
                    .reply(isReply)
                    .intentType(intentType)
                    .missingDetails(missing)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse intent response", e);
            return GeminiEmailIntentResponse.builder()
                    .procurementRelated(false)
                    .hasAllDetails(false)
                    .reply(false)
                    .intentType(GeminiEmailIntentResponse.IntentType.NOT_PROCUREMENT)
                    .reasoning("Parse error")
                    .build();
        }
    }

    private GeminiInventoryMatchResponse parseInventoryMatchResponse(Map<String, Object> response) {
        try {
            String text = extractTextFromGeminiResponse(response);
            log.info("Gemini inventory match response: {}", text);

            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            JsonNode root = OBJECT_MAPPER.readTree(text);
            List<String> matchingIds = new ArrayList<>();
            JsonNode idsNode = root.get("matchingItemIds");
            if (idsNode != null && idsNode.isArray()) {
                idsNode.forEach(node -> {
                    if (node != null && node.isTextual() && !node.asText().isBlank()) {
                        matchingIds.add(node.asText());
                    }
                });
            }

            String reasoning = null;
            if (root.has("reasoning") && root.get("reasoning").isTextual()) {
                reasoning = root.get("reasoning").asText();
            }

            return GeminiInventoryMatchResponse.builder()
                    .matchingItemIds(matchingIds)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse inventory match response", e);
            return GeminiInventoryMatchResponse.builder()
                    .matchingItemIds(Collections.emptyList())
                    .reasoning("Parse error")
                    .build();
        }
    }

    private Map<String, Object> buildGeminiRequest(String prompt) {
        // OpenRouter uses messages array format like OpenAI
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> request = new HashMap<>();
        request.put("messages", Collections.singletonList(message));
        // Model will be added in postToGemini method

        return request;
    }

    private String buildInventoryMatchPrompt(GeminiInventoryMatchRequest request) {
        StringBuilder specsSection = new StringBuilder();
        if (request.getSpecifications() != null && !request.getSpecifications().isEmpty()) {
            specsSection.append("- Specifications:\n");
            request.getSpecifications().forEach((key, value) ->
                    specsSection.append(String.format("  - %s: %s\n", key, value))
            );
        }

        StringBuilder inventorySection = new StringBuilder();
        int index = 1;
        for (GeminiInventoryMatchRequest.InventoryItemSummary item : request.getInventoryItems()) {
            inventorySection.append(String.format(
                    "%d. %s :: %s (Type=%s, Available=%d)\n",
                    index++,
                    item.getItemId(),
                    item.getItemName(),
                    item.getItemType(),
                    item.getAvailableQuantity() == null ? 0 : item.getAvailableQuantity()
            ));
        }

        return String.format(
                "You are an inventory specialist matching a procurement request to on-hand stock.\n" +
                "Select items whose type matches the user's requested item. Favor exact type matches; ignore unrelated items.\n" +
                "If nothing matches, return an empty list.\n\n" +
                "PROCUREMENT REQUEST:\n" +
                "- Item Type: %s\n" +
                "- Item Name: %s\n" +
                "- Quantity: %s\n" +
                "%s\n" +
                "INVENTORY CATALOG (ID :: Name):\n%s\n" +
                "Respond in compact JSON with: {\n" +
                "  \"matchingItemIds\": [list of IDs that match],\n" +
                "  \"reasoning\": \"short explanation\"\n" +
                "}\n" +
                "Only return IDs that appear in the catalog above.",
                Optional.ofNullable(request.getItemType()).orElse("unknown"),
                Optional.ofNullable(request.getItemName()).orElse("unknown"),
                Optional.ofNullable(request.getQuantity()).map(Object::toString).orElse("unknown"),
                specsSection,
                inventorySection
        );
    }

    private String buildConfirmationPrompt(GeminiConfirmationRequest request) {
        StringBuilder candidatesSection = new StringBuilder();
        if (request.getCandidateItems() == null || request.getCandidateItems().isEmpty()) {
            candidatesSection.append("- No inventory candidates provided. Assume new purchase unless the user references a known ID.\n");
        } else {
            candidatesSection.append("- Here are the only valid inventory options (ID :: Name). If none match, leave the list empty.\n");
            request.getCandidateItems().forEach(item ->
                    candidatesSection.append(String.format("  - %s :: %s\n",
                            item.getItemId(), item.getItemName()))
            );
        }

        return String.format(
                "You are a procurement assistant helping interpret a confirmation reply.\n" +
                "Sender email: %s\n" +
                "Subject: %s\n" +
                "Reply Body (trimmed):\n%s\n\n" +
                "CONTEXT:\n" +
                "%s\n" +
                "TASK:\n" +
                "1. Determine if the user confirmed proceeding with procurement.\n" +
                "2. Match the confirmation to one or more item IDs from the inventory list above.\n" +
                "3. Only return IDs that appear in the inventory list. If nothing matches, return an empty list.\n" +
                "4. If the user declines or is unclear, mark confirmed=false.\n" +
                "5. Keep the response concise.\n\n" +
                "Respond ONLY in JSON using this format:\n" +
                "{\n" +
                "  \"confirmed\": true|false,\n" +
                "  \"selectedItemIds\": [\"ID-1\", \"ID-2\"],\n" +
                "  \"reasoning\": \"very short explanation\"\n" +
                "}\n",
                request.getSenderEmail(),
                request.getEmailSubject(),
                request.getEmailBody(),
                candidatesSection
        );
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

    private GeminiConfirmationResponse parseConfirmationResponse(Map<String, Object> response) {
        try {
            String text = extractTextFromGeminiResponse(response);
            log.info("Gemini confirmation parsing response: {}", text);

            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            JsonNode root = OBJECT_MAPPER.readTree(text);
            boolean confirmed = root.path("confirmed").asBoolean(false);

            List<String> selectedIds = new ArrayList<>();
            JsonNode idsNode = root.get("selectedItemIds");
            if (idsNode != null && idsNode.isArray()) {
                idsNode.forEach(node -> {
                    if (node.isTextual() && !node.asText().isBlank()) {
                        selectedIds.add(node.asText().trim());
                    }
                });
            }

            String reasoning = null;
            if (root.has("reasoning") && root.get("reasoning").isTextual()) {
                reasoning = root.get("reasoning").asText();
            }

            return GeminiConfirmationResponse.builder()
                    .confirmed(confirmed)
                    .selectedItemIds(selectedIds)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse confirmation response", e);
            return GeminiConfirmationResponse.builder()
                    .confirmed(false)
                    .selectedItemIds(Collections.emptyList())
                    .reasoning("Parse error")
                    .build();
        }
    }

    private Map<String, Object> postToGemini(String operation, Map<String, Object> payload) {
        // OpenRouter uses chat completions format
        requestResponseLogger.logRequest(operation, payload);
        try {
            // Add model to payload
            payload.put("model", openRouterModel);
            
            Map<String, Object> response = geminiRestTemplate.postForObject(
                    geminiApiUrl, payload, Map.class);
            requestResponseLogger.logResponse(operation, response);
            return response;
        } catch (RestClientException e) {
            requestResponseLogger.logResponse(operation, Collections.singletonMap("error", e.getMessage()));
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<String, Object> response) {
        // OpenRouter response format: {"choices": [{"message": {"content": "..."}}]}
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AIAnalysisException("No choices in OpenRouter response");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new AIAnalysisException("No message in OpenRouter response");
        }

        String content = (String) message.get("content");
        if (content == null || content.isEmpty()) {
            throw new AIAnalysisException("No content in OpenRouter response");
        }

        return content;
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
