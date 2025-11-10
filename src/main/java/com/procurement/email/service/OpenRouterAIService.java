package com.procurement.email.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.email.integration.dto.*;
import com.procurement.email.model.EmailMessage;
import com.procurement.email.model.Item;
import com.procurement.email.model.ProcurementRequest;
import com.procurement.email.model.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service for integrating with OpenRouter AI API for email classification,
 * request extraction, and item recommendation.
 * This service uses OpenRouter's unified API to access various LLM models.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "ai.provider", havingValue = "openrouter")
public class OpenRouterAIService implements AIService {

    private final RestTemplate openRouterRestTemplate;
    private final String openRouterApiUrl;
    private final String openRouterModel;
    private final int maxRetries;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final GeminiRequestResponseLogger requestResponseLogger;

    public OpenRouterAIService(
            @Qualifier("openRouterRestTemplate") RestTemplate openRouterRestTemplate,
            @Value("${openrouter.api.url}") String openRouterApiUrl,
            @Value("${openrouter.api.model:openai/gpt-4o-mini}") String openRouterModel,
            @Value("${openrouter.api.max-retries:3}") int maxRetries,
            GeminiRequestResponseLogger requestResponseLogger) {
        this.openRouterRestTemplate = openRouterRestTemplate;
        this.openRouterApiUrl = openRouterApiUrl;
        this.openRouterModel = openRouterModel;
        this.maxRetries = maxRetries;
        this.requestResponseLogger = requestResponseLogger;
        log.info("OpenRouterAIService initialized with model: {}", openRouterModel);
    }

    /**
     * Analyzes email intent using OpenRouter API
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
            // Call Gemini's actual method logic but route through OpenRouter
            return determineEmailIntentWithOpenRouter(request);
        } catch (Exception e) {
            log.error("Error analyzing email intent: {}", e.getMessage(), e);
            // Return safe default matching Gemini's fallback
            return GeminiEmailIntentResponse.builder()
                    .procurementRelated(false)
                    .reply(false)
                    .hasAllDetails(false)
                    .intentType(GeminiEmailIntentResponse.IntentType.NOT_PROCUREMENT)
                    .reasoning("Fallback due to analysis error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Internal method that uses OpenRouter API to determine email intent
     */
    private GeminiEmailIntentResponse determineEmailIntentWithOpenRouter(GeminiEmailIntentRequest request) {
        String prompt = buildGeminiStyleIntentPrompt(request);
        String response = callOpenRouterAPI(prompt);
        
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response);
            
            boolean isProcurementRelated = json.path("procurementRelated").asBoolean(false);
            boolean isReply = json.path("reply").asBoolean(false);
            boolean hasAllDetails = json.path("hasAllDetails").asBoolean(false);
            
            String intentTypeStr = json.path("intentType").asText("NOT_PROCUREMENT");
            GeminiEmailIntentResponse.IntentType intentType;
            try {
                intentType = GeminiEmailIntentResponse.IntentType.valueOf(intentTypeStr);
            } catch (IllegalArgumentException e) {
                intentType = GeminiEmailIntentResponse.IntentType.NOT_PROCUREMENT;
            }
            
            List<String> missingDetails = new ArrayList<>();
            JsonNode missingNode = json.path("missingDetails");
            if (missingNode.isArray()) {
                missingNode.forEach(node -> missingDetails.add(node.asText()));
            }
            
            return GeminiEmailIntentResponse.builder()
                    .procurementRelated(isProcurementRelated)
                    .reply(isReply)
                    .hasAllDetails(hasAllDetails)
                    .intentType(intentType)
                    .missingDetails(missingDetails)
                    .reasoning(json.path("reasoning").asText(""))
                    .build();
        } catch (Exception e) {
            log.error("Error parsing intent response: {}", e.getMessage());
            throw new AIAnalysisException("Failed to parse email intent response", e);
        }
    }

    /**
     * Builds prompt in same style as Gemini for email intent analysis
     */
    private String buildGeminiStyleIntentPrompt(GeminiEmailIntentRequest request) {
        return String.format("""
                You are analyzing an email to determine if it's a procurement request.
                
                Email Details:
                - From: %s
                - Subject: %s
                - Body: %s
                - In-Reply-To: %s
                
                Analyze this email and determine:
                1. Is it procurement-related? (requesting items/equipment/supplies)
                2. Is it a reply to a previous email? (based on In-Reply-To)
                3. Does it have all necessary details? (item type and quantity minimum)
                4. What is the intent type?
                   - NEW_REQUEST_WITH_DETAILS: New request with item type and quantity
                   - NEW_REQUEST_MISSING_DETAILS: New request but missing required info
                   - REPLY_CONFIRMATION: Reply confirming/selecting an item
                   - REPLY_INFORMATION: Reply providing additional information
                   - NOT_PROCUREMENT: Not related to procurement
                5. What details are missing (if any)?
                
                Return ONLY valid JSON:
                {
                  "procurementRelated": true/false,
                  "reply": true/false,
                  "hasAllDetails": true/false,
                  "intentType": "NEW_REQUEST_WITH_DETAILS|NEW_REQUEST_MISSING_DETAILS|REPLY_CONFIRMATION|REPLY_INFORMATION|NOT_PROCUREMENT",
                  "missingDetails": ["list", "of", "missing", "items"],
                  "reasoning": "brief explanation"
                }
                """,
                request.getSenderEmail(),
                request.getEmailSubject(),
                request.getEmailBody(),
                request.getInReplyToMessageId() != null ? request.getInReplyToMessageId() : "none"
        );
    }

    /**
     * Validates procurement email template
     */
    public EmailProcessingService.TemplateValidationResult validateProcurementEmail(EmailMessage email) {
        log.debug("Validating procurement email template for message: {}", email.getMessageId());

        try {
            String prompt = buildValidationPrompt(email);
            String response = callOpenRouterAPI(prompt);
            return parseValidationResponse(response);
        } catch (Exception e) {
            log.error("Error validating email: {}", e.getMessage(), e);
            return new EmailProcessingService.TemplateValidationResult(
                    false,
                    Collections.singletonList("Validation error: " + e.getMessage())
            );
        }
    }

    /**
     * Checks if email is procurement-related
     */
    public boolean isProcurementRelated(EmailMessage email) {
        GeminiEmailIntentResponse intent = analyzeEmailIntent(email);
        return intent.isProcurementRelated();
    }

    /**
     * Extracts procurement request details from email
     */
    public ProcurementRequest extractRequestDetails(EmailMessage email) {
        log.debug("Extracting procurement request details from email: {}", email.getMessageId());

        try {
            String prompt = buildExtractionPrompt(email);
            String response = callOpenRouterAPI(prompt);
            return parseExtractionResponse(response, email);
        } catch (Exception e) {
            log.error("Error extracting request details: {}", e.getMessage(), e);
            throw new AIAnalysisException("Failed to extract request details", e);
        }
    }

    /**
     * Parses confirmation email
     */
    public GeminiConfirmationResponse parseConfirmationEmail(GeminiConfirmationRequest request) {
        log.debug("Parsing confirmation email");

        try {
            String prompt = buildConfirmationPrompt(request);
            String response = callOpenRouterAPI(prompt);
            return parseConfirmationResponse(response);
        } catch (Exception e) {
            log.error("Error parsing confirmation: {}", e.getMessage(), e);
            throw new AIAnalysisException("Failed to parse confirmation", e);
        }
    }

    /**
     * Recommends items based on request
     */
    public List<Item> recommendItems(
            ProcurementRequest request,
            UserContext userContext,
            List<Item> availableItems) {

        log.debug("Recommending items for: {} (Type: {})", request.getItemName(), request.getItemType());

        try {
            // Convert specifications map to string
            String specsString = "";
            if (request.getSpecifications() != null && !request.getSpecifications().isEmpty()) {
                specsString = request.getSpecifications().entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
            }
            
            String prompt = buildRecommendationPrompt(
                    request.getItemName(),
                    request.getItemType(),
                    specsString,
                    request.getQuantity(),
                    userContext,
                    availableItems);
            String response = callOpenRouterAPI(prompt);
            return parseRecommendationResponse(response, availableItems);
        } catch (Exception e) {
            log.error("Error recommending items: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Matches inventory items with procurement request
     */
    public GeminiInventoryMatchResponse matchInventoryItems(ProcurementRequest request, List<Item> inventoryItems) {
        log.debug("Matching inventory items for request: {}", request.getItemName());

        try {
            String prompt = buildInventoryMatchPrompt(request, inventoryItems);
            String response = callOpenRouterAPI(prompt);
            return parseInventoryMatchResponse(response, inventoryItems);
        } catch (Exception e) {
            log.error("Error matching inventory: {}", e.getMessage(), e);
            return GeminiInventoryMatchResponse.builder()
                    .matchingItemIds(Collections.emptyList())
                    .reasoning("Error during matching: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Generates RRF (Request for Requisition) document
     */
    public String generateRRFDocument(String prompt) {
        log.debug("Generating RRF document");

        try {
            // Call API without JSON mode since we need HTML
            String response = callOpenRouterAPIForHTML(prompt);
            // Extract the actual content (remove markdown wrappers if present)
            return extractTextContent(response);
        } catch (Exception e) {
            log.error("Error generating RRF: {}", e.getMessage(), e);
            throw new AIAnalysisException("Failed to generate RRF document", e);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Core method to call OpenRouter API
     */
    private String callOpenRouterAPI(String prompt) {
        return callOpenRouterAPIWithRetry(prompt, 0, true);
    }

    /**
     * Call OpenRouter API without JSON mode (for HTML generation)
     */
    private String callOpenRouterAPIForHTML(String prompt) {
        return callOpenRouterAPIWithRetry(prompt, 0, false);
    }

    private String callOpenRouterAPIWithRetry(String prompt, int attempt, boolean enforceJson) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openRouterModel);
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);
            
            requestBody.put("messages", messages);

            // Optional: Add temperature and other parameters
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 4000);
            
            // Request JSON-only responses only when enforceJson is true
            if (enforceJson) {
                Map<String, String> responseFormat = new HashMap<>();
                responseFormat.put("type", "json_object");
                requestBody.put("response_format", responseFormat);
                log.debug("Enforcing JSON response format");
            } else {
                log.debug("Allowing free-form response (for HTML generation)");
            }

            log.debug("Calling OpenRouter API with model: {}", openRouterModel);
            requestResponseLogger.logRequest("OpenRouter", requestBody);

            String response = openRouterRestTemplate.postForObject(
                    openRouterApiUrl,
                    requestBody,
                    String.class
            );

            requestResponseLogger.logResponse("OpenRouter", response);

            if (response == null || response.isEmpty()) {
                throw new AIAnalysisException("Empty response from OpenRouter API");
            }

            // Parse and extract the message content
            return extractMessageContent(response);

        } catch (RestClientException e) {
            log.warn("OpenRouter API call failed (attempt {}/{}): {}", attempt + 1, maxRetries, e.getMessage());
            
            if (attempt < maxRetries - 1) {
                try {
                    Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return callOpenRouterAPIWithRetry(prompt, attempt + 1, enforceJson);
            }
            
            throw new AIAnalysisException("OpenRouter API call failed after " + maxRetries + " attempts", e);
        }
    }

    /**
     * Extracts message content from OpenRouter response
     */
    private String extractMessageContent(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            JsonNode choices = root.path("choices");
            
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                String content = firstChoice.path("message").path("content").asText();
                
                if (content != null && !content.isEmpty()) {
                    return content;
                }
            }
            
            throw new AIAnalysisException("Unable to extract message content from response");
        } catch (Exception e) {
            log.error("Error parsing OpenRouter response: {}", e.getMessage());
            throw new AIAnalysisException("Failed to parse OpenRouter response", e);
        }
    }

    /**
     * Extracts text content (removes markdown code block wrappers)
     * Handles both ```json and ```html markdown blocks
     */
    private String extractTextContent(String response) {
        String content = response.trim();
        
        // Remove markdown code blocks if present
        // Examples: ```html ... ```, ```json ... ```, ``` ... ```
        if (content.startsWith("```")) {
            // Find the end of the first line (language specifier)
            int firstNewline = content.indexOf("\n");
            if (firstNewline != -1) {
                content = content.substring(firstNewline + 1);
            } else {
                // No newline after ```, just remove ```
                content = content.substring(3);
            }
        }
        
        // Remove closing ```
        if (content.endsWith("```")) {
            int lastTripleBacktick = content.lastIndexOf("```");
            content = content.substring(0, lastTripleBacktick);
        }
        
        return content.trim();
    }

    // ==================== Prompt Builders ====================
    // Note: Gemini-style prompts are built inline in the methods above
    // to match the exact same format and behavior as GeminiAIService

    private String buildValidationPrompt(EmailMessage email) {
        return String.format("""
                Validate this procurement email for required information.
                
                Subject: %s
                Body: %s
                
                Required fields:
                - Item Type (e.g., laptop, monitor, keyboard)
                - Quantity (how many items)
                
                Optional fields:
                - Specifications (details about the item)
                - Item Name (specific model or name)
                
                IMPORTANT: Return ONLY valid JSON, no other text or explanation.
                
                {
                  "isValid": true/false,
                  "missingFields": ["item_type", "quantity"]
                }
                
                If valid, missingFields should be an empty array.
                """,
                email.getSubject(),
                email.getBody()
        );
    }

    private String buildExtractionPrompt(EmailMessage email) {
        return String.format("""
                Extract procurement request details from this email.
                
                Subject: %s
                Body: %s
                
                Extract: item name, item type, quantity, specifications, urgency
                
                IMPORTANT: Return ONLY valid JSON, no other text or explanation.
                
                {
                  "itemName": "...",
                  "itemType": "...",
                  "quantity": number,
                  "specifications": "...",
                  "urgency": "HIGH/MEDIUM/LOW"
                }
                """,
                email.getSubject(),
                email.getBody()
        );
    }

    private String buildConfirmationPrompt(GeminiConfirmationRequest request) {
        // Build candidate items list
        StringBuilder candidatesList = new StringBuilder();
        if (request.getCandidateItems() != null) {
            for (GeminiConfirmationRequest.CandidateItem item : request.getCandidateItems()) {
                candidatesList.append(String.format("- ID: %s, Name: %s\n", item.getItemId(), item.getItemName()));
            }
        }
        
        return String.format("""
                Parse this confirmation email.
                
                Body: %s
                Available items: 
                %s
                
                Determine which item was confirmed and extract details.
                
                IMPORTANT: Return ONLY valid JSON, no other text or explanation.
                
                {
                  "isConfirmation": true/false,
                  "confirmedItemId": "...",
                  "confirmedItemName": "...",
                  "quantity": number
                }
                """,
                request.getEmailBody(),
                candidatesList.toString()
        );
    }

    private String buildRecommendationPrompt(String itemName, String itemType, String specifications,
                                              int quantity, UserContext userContext, List<Item> availableItems) {
        StringBuilder itemsList = new StringBuilder();
        for (Item item : availableItems) {
            itemsList.append(String.format("- ID: %s, Name: %s, Type: %s, Specs: %s, Available: %d\n",
                    item.getId(), item.getName(), item.getType(), item.getSpecifications(), item.getAvailableQuantity()));
        }

        return String.format("""
                Recommend items from inventory for this request:
                
                Requested Item: %s
                Type: %s
                Specifications: %s
                Quantity: %d
                
                Requester Details:
                - Role: %s
                - Designation: %s
                - Preferences: %s
                
                Available Items:
                %s
                
                Return item IDs that best match, in order of relevance.
                
                IMPORTANT: Return ONLY valid JSON, no other text or explanation.
                
                {
                  "recommendedItemIds": ["id1", "id2", "id3"]
                }
                """,
                itemName, itemType, specifications, quantity,
                userContext.getRole(), userContext.getDesignation(), userContext.getPreferences(),
                itemsList.toString()
        );
    }

    private String buildInventoryMatchPrompt(ProcurementRequest request, List<Item> inventoryItems) {
        StringBuilder itemsList = new StringBuilder();
        for (Item item : inventoryItems) {
            itemsList.append(String.format("- ID: %s, Name: %s, Type: %s, Specs: %s, Available: %d\n",
                    item.getId(), item.getName(), item.getType(), item.getSpecifications(), item.getAvailableQuantity()));
        }

        // Convert specifications map to string
        String specsString = "";
        if (request.getSpecifications() != null && !request.getSpecifications().isEmpty()) {
            specsString = request.getSpecifications().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        }

        return String.format("""
                Match inventory items with this procurement request:
                
                Request:
                - Item: %s
                - Type: %s
                - Quantity: %d
                - Specifications: %s
                
                Available Inventory:
                %s
                
                Return the IDs of items that match the request, in order of relevance.
                
                IMPORTANT: Return ONLY valid JSON, no other text or explanation.
                
                {
                  "matchedItemIds": ["id1", "id2"],
                  "reasoning": "explanation of why these items match"
                }
                """,
                request.getItemName(),
                request.getItemType(),
                request.getQuantity(),
                specsString,
                itemsList.toString()
        );
    }

    // ==================== Response Parsers ====================
    // Note: Intent response parsing is now done inline in determineEmailIntentWithOpenRouter

    private EmailProcessingService.TemplateValidationResult parseValidationResponse(String response) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response);
            boolean isValid = json.path("isValid").asBoolean(false);
            
            // Extract missing fields as a list
            List<String> missingFields = new ArrayList<>();
            JsonNode missingNode = json.path("missingFields");
            if (missingNode.isArray()) {
                missingNode.forEach(node -> missingFields.add(node.asText()));
            } else if (!isValid) {
                // If not valid but no missing fields array, use reason as missing field
                String reason = json.path("reason").asText("");
                if (!reason.isEmpty()) {
                    missingFields.add(reason);
                }
            }
            
            return new EmailProcessingService.TemplateValidationResult(isValid, missingFields);
        } catch (Exception e) {
            log.error("Error parsing validation response: {}", e.getMessage());
            return new EmailProcessingService.TemplateValidationResult(
                    false,
                    Collections.singletonList("Parse error: " + e.getMessage())
            );
        }
    }

    private ProcurementRequest parseExtractionResponse(String response, EmailMessage email) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response);
            
            ProcurementRequest request = new ProcurementRequest();
            request.setRequestId(UUID.randomUUID().toString());
            request.setRequesterEmail(email.getFrom());
            request.setItemName(json.path("itemName").asText(""));
            request.setItemType(json.path("itemType").asText(""));
            request.setQuantity(json.path("quantity").asInt(1));
            
            // Parse specifications as a map
            Map<String, String> specifications = new HashMap<>();
            JsonNode specsNode = json.path("specifications");
            if (specsNode.isObject()) {
                specsNode.fields().forEachRemaining(entry -> {
                    specifications.put(entry.getKey(), entry.getValue().asText());
                });
            } else if (specsNode.isTextual()) {
                // If it's a string, parse it as key:value pairs
                String specsText = specsNode.asText();
                if (!specsText.isEmpty()) {
                    specifications.put("description", specsText);
                }
            }
            request.setSpecifications(specifications);
            
            request.setRequestDate(email.getReceivedDate());
            request.setCandidateItemIds(Collections.emptyList());
            
            return request;
        } catch (Exception e) {
            log.error("Error parsing extraction response: {}", e.getMessage());
            throw new AIAnalysisException("Failed to parse extraction response", e);
        }
    }

    private GeminiConfirmationResponse parseConfirmationResponse(String response) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response);
            
            boolean confirmed = json.path("isConfirmation").asBoolean(false);
            List<String> selectedItemIds = new ArrayList<>();
            
            // Check for confirmedItemId (single item)
            String confirmedItemId = json.path("confirmedItemId").asText("");
            if (!confirmedItemId.isEmpty()) {
                selectedItemIds.add(confirmedItemId);
            }
            
            // Also check for selectedItemIds array
            JsonNode selectedIds = json.path("selectedItemIds");
            if (selectedIds.isArray()) {
                selectedIds.forEach(node -> selectedItemIds.add(node.asText()));
            }
            
            String reasoning = json.path("reasoning").asText("");
            if (reasoning.isEmpty()) {
                reasoning = json.path("confirmedItemName").asText("");
            }
            
            return GeminiConfirmationResponse.builder()
                    .confirmed(confirmed)
                    .selectedItemIds(selectedItemIds)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing confirmation response: {}", e.getMessage());
            throw new AIAnalysisException("Failed to parse confirmation response", e);
        }
    }

    private List<Item> parseRecommendationResponse(String response, List<Item> availableItems) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response);
            JsonNode recommendedIds = json.path("recommendedItemIds");
            
            List<Item> recommendations = new ArrayList<>();
            if (recommendedIds.isArray()) {
                for (JsonNode idNode : recommendedIds) {
                    String itemId = idNode.asText();
                    availableItems.stream()
                            .filter(item -> item.getId().equals(itemId))
                            .findFirst()
                            .ifPresent(recommendations::add);
                }
            }
            
            return recommendations;
        } catch (Exception e) {
            log.error("Error parsing recommendation response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private GeminiInventoryMatchResponse parseInventoryMatchResponse(String response, List<Item> inventoryItems) {
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response);
            String reasoning = json.path("reasoning").asText("");
            
            List<String> matchingItemIds = new ArrayList<>();
            JsonNode matchedIds = json.path("matchedItemIds");
            if (matchedIds.isArray()) {
                for (JsonNode idNode : matchedIds) {
                    matchingItemIds.add(idNode.asText());
                }
            }
            
            return GeminiInventoryMatchResponse.builder()
                    .matchingItemIds(matchingItemIds)
                    .reasoning(reasoning)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing inventory match response: {}", e.getMessage());
            return GeminiInventoryMatchResponse.builder()
                    .matchingItemIds(Collections.emptyList())
                    .reasoning("Parse error: " + e.getMessage())
                    .build();
        }
    }

    // ==================== Exception Class ====================

    public static class AIAnalysisException extends RuntimeException {
        public AIAnalysisException(String message) {
            super(message);
        }

        public AIAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
