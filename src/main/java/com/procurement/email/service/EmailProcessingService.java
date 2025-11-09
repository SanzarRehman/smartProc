package com.procurement.email.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.email.integration.dto.GeminiConfirmationRequest;
import com.procurement.email.integration.dto.GeminiConfirmationResponse;
import com.procurement.email.integration.dto.GeminiEmailIntentResponse;
import com.procurement.email.integration.dto.GeminiInventoryMatchResponse;
import com.procurement.email.model.*;
import com.procurement.email.repository.EmailProcessingStateRepository;
import com.procurement.email.service.GeminiAIService.AIAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service responsible for orchestrating the complete email processing workflow.
 * Coordinates between AI analysis, user context retrieval, inventory checking,
 * procurement, and accounting services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingService {

    private final GeminiAIService geminiAIService;
    private final KeycloakIntegrationService keycloakIntegrationService;
    private final InventoryService inventoryService;
    private final InventoryVectorService inventoryVectorService;
    private final EmailSenderService emailSenderService;
    private final ProcurementAgentService procurementAgentService;
    private final AccountingAgentService accountingAgentService;
    private final EmailProcessingStateRepository emailProcessingStateRepository;
    private final EmailCleaningService emailCleaningService;
    private final ObjectMapper objectMapper;
    private final HumanInTheMiddleService humanInTheMiddleService;
    // Workflow state constants
    private static final String STATE_EMAIL_RECEIVED = "EMAIL_RECEIVED";
    private static final String STATE_CLASSIFIED = "CLASSIFIED";
    private static final String STATE_CONTEXT_GATHERED = "CONTEXT_GATHERED";
    private static final String STATE_INVENTORY_CHECKED = "INVENTORY_CHECKED";
    private static final String STATE_RECOMMENDATIONS_SENT = "RECOMMENDATIONS_SENT";
    private static final String STATE_CONFIRMATION_RECEIVED = "CONFIRMATION_RECEIVED";
    private static final String STATE_PO_GENERATED = "PO_GENERATED";
    private static final String STATE_ACCOUNTING_UPDATED = "ACCOUNTING_UPDATED";
    private static final String STATE_COMPLETION_SENT = "COMPLETION_SENT";
    private static final String STATE_NOT_PROCUREMENT = "NOT_PROCUREMENT";
    private static final String STATE_USER_NOT_FOUND = "USER_NOT_FOUND";
    private static final String STATE_ERROR = "ERROR";

    /**
     * Processes an incoming email through the procurement workflow.
     * This is the main entry point for new procurement request emails.
     *
     * @param email the incoming email message
     */
    @Transactional
    public void processIncomingEmail(EmailMessage email) {
        log.info("Processing incoming email from: {}, subject: {}", email.getFrom(), email.getSubject());

        // STEP 1: Determine PO number - use existing if reply, generate new if not
        String poNumber = null;
        boolean isReply = false;
        
        // Check if this is a reply to an existing thread
        if (email.getInReplyTo() != null && !email.getInReplyTo().isEmpty()) {
            log.debug("Email is a reply, looking for existing thread with In-Reply-To: {}", email.getInReplyTo());
            poNumber = emailCleaningService.getExistingPoNumberIfExists(email);
            
            if (poNumber != null) {
                log.info("Found existing thread with PO number: {}", poNumber);
                isReply = true;
            } else {
                log.debug("No existing thread found for reply, will generate new PO number");
            }
        }
        
        // Generate new PO number if not found
        if (poNumber == null) {
            poNumber = generatePoNumber();
            log.info("Generated new PO number {} for email {}", poNumber, email.getMessageId());
        }

        EmailProcessingState state = null;

        try {
            Optional<UserContext> userContextOpt = getUserContext(email.getFrom());
            if (userContextOpt.isEmpty()) {
                log.warn("Unauthorized procurement attempt from: {}", email.getFrom());
                emailSenderService.sendErrorEmail(email.getFrom(),
                        "We couldn't find your account in the procurement system. Please register before submitting requests.",
                        email.getMessageId(), poNumber);
                return;
            }

            UserContext userContext = userContextOpt.get();

            // STEP 2: Classify email intent
            GeminiEmailIntentResponse intent = geminiAIService.analyzeEmailIntent(email);

            // STEP 3: Save email thread regardless of procurement status
            boolean isProcurement = intent.isProcurementRelated();
            String processingState = isProcurement ? STATE_CLASSIFIED : STATE_NOT_PROCUREMENT;
            emailCleaningService.saveEmailThread(email, poNumber, isProcurement, processingState);
            log.info("Saved email thread with PO number {} and state {}", poNumber, processingState);

            // STEP 4: Start or continue workflow with human in the middle
            if (!isReply) {
                humanInTheMiddleService.startProcess(email.getFrom(), poNumber, email.getMessageId(), 
                    email.getSubject(), userContext.getUsername(), false);
                humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(), 
                    email.getSubject(), userContext.getUsername(), false, "send");
            } else {
                humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(), 
                    email.getSubject(), userContext.getUsername(), false, "send");
            }

            if (!intent.isProcurementRelated()) {
                log.info("Email is not procurement-related. Saved with PO number {} for tracking.", poNumber);
                // Still save the processing state for non-procurement emails
                state = createInitialStateWithPoNumber(email, poNumber);
                updateState(state, STATE_NOT_PROCUREMENT, null);
                return;
            }

            if (intent.getIntentType() == GeminiEmailIntentResponse.IntentType.REPLY_CONFIRMATION) {
                log.debug("Detected confirmation reply. Delegating to confirmation workflow for message {}", email.getMessageId());
                processConfirmationEmail(email, poNumber);
                return;
            }

            if (intent.getIntentType() == GeminiEmailIntentResponse.IntentType.REPLY_INFORMATION) {
                log.info("Reply {} contains additional details; continuing through intake flow instead of confirmation handling.", email.getMessageId());
            } else if (intent.isReply()) {
                log.debug("Detected reply without new details. Delegating to confirmation workflow for message {}", email.getMessageId());
                processConfirmationEmail(email, poNumber);
                return;
            }

            if (!intent.isHasAllDetails() || intent.getIntentType() == GeminiEmailIntentResponse.IntentType.NEW_REQUEST_MISSING_DETAILS) {
                log.warn("New procurement email missing critical details. Requesting additional information.");
                List<String> missing = intent.getMissingDetails();
                if (missing == null || missing.isEmpty()) {
                    missing = List.of("Item Type", "Quantity");
                }
                emailSenderService.sendTemplateGuideEmail(email.getFrom(), missing, email.getMessageId(), email.getSubject(), poNumber);
                humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(), email.getSubject(), "service", false, "send");
                return;
            }

            state = createInitialStateWithPoNumber(email, poNumber);
            updateState(state, STATE_CLASSIFIED, null);

            EmailMessage cleanedEmail = createCleanedEmailCopy(email);
            TemplateValidationResult validationResult = validateProcurementOrFallback(cleanedEmail, email, poNumber);
            if (!validationResult.isValid()) {
                updateState(state, "TEMPLATE_INVALID", null);
                return;
            }

            log.debug("Extracting procurement request details");
            ProcurementRequest request = extractRequestDetails(cleanedEmail);
            request.setPoNumber(poNumber); // Set PO number for tracking

            log.debug("Loading available inventory for Gemini matching");
            List<Item> allInventory = inventoryService.listAllAvailableItems();
            GeminiInventoryMatchResponse matchResponse = geminiAIService.matchInventoryItems(request, allInventory);
            List<Item> matchedItems = inventoryService.getItemsByIds(matchResponse.getMatchingItemIds());

            if (!matchedItems.isEmpty()) {
                request.setCandidateItemIds(matchResponse.getMatchingItemIds());
                log.info("Gemini identified {} candidate inventory items", matchedItems.size());
            } else {
                request.setCandidateItemIds(Collections.emptyList());
                log.info("Gemini did not find matching inventory items: {}", matchResponse.getReasoning());
            }
            state.setProcurementRequestId(request.getRequestId());
            updateState(state, STATE_CONTEXT_GATHERED, request);

            List<Item> availableItems;
            if (!matchedItems.isEmpty()) {
                availableItems = matchedItems;
            } else {
                log.debug("Checking inventory for available items via fallback search");
                availableItems = checkInventory(request);
            }
            updateState(state, STATE_INVENTORY_CHECKED, request);

            List<Item> itemsToRecommend = availableItems;
            if (!availableItems.isEmpty()) {
                log.debug("Requesting Gemini recommendations with matched inventory context");
                List<Item> recommendedItems = getRecommendations(request, userContext, availableItems);
                if (!recommendedItems.isEmpty()) {
                    itemsToRecommend = recommendedItems;
                } else {
                    log.debug("Gemini returned no ranked recommendations; defaulting to matched inventory list.");
                }
            }

            // Use vector search to find similar products based on the request
            log.debug("Searching for similar products using vector database");
            Map<String, Float> similarProductsWithScores = getSimilarProducts(request);

            if (!availableItems.isEmpty()) {
                sendRecommendations(email.getFrom(), itemsToRecommend, similarProductsWithScores, request, email.getMessageId(), email.getSubject());
                humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(),
                    email.getSubject(), userContext.getUsername(), false, "send");
            } else {
                sendNewPurchaseConfirmation(email.getFrom(), request, email.getMessageId(), email.getSubject());
                humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(),
                    email.getSubject(), userContext.getUsername(), false, "send");
            }
            updateState(state, STATE_RECOMMENDATIONS_SENT, request);

            log.info("Successfully processed incoming email. State: {}", state.getCurrentState());

        } catch (AIAnalysisException e) {
            log.error("AI analysis failed for email: {}", email.getMessageId(), e);
            handleAIAnalysisError(email, state, e);
        } catch (UserNotFoundException e) {
            log.error("User not found: {}", email.getFrom(), e);
            handleUserNotFoundError(email, state);
        } catch (InventoryServiceException e) {
            log.error("Inventory service error for email: {}", email.getMessageId(), e);
            handleInventoryError(email, state, e);
        } catch (Exception e) {
            log.error("Unexpected error processing email: {}", email.getMessageId(), e);
            handleGenericError(email, state, e);
        }
    }


    private boolean isConfirmationEmail(EmailMessage email) {
        if (email == null) {
            return false;
        }
        boolean isReply = email.getInReplyTo() != null && !email.getInReplyTo().isEmpty();
      return isReply;
       }

    /**
     * Processes a confirmation email from the requester.
     * Handles the user's response to item recommendations.
     *
     * @param email the confirmation email message
     */
    @Transactional
    public void processConfirmationEmail(EmailMessage email,String poNumber) {
        log.info("Processing confirmation email from: {}, subject: {}", email.getFrom(), email.getSubject());

        EmailProcessingState state = null;

        try {
            // Retrieve processing state from database
            log.debug("Step 1: Retrieving processing state");
            state = retrieveProcessingState(email);

            if (state == null) {
                log.warn("No processing state found for confirmation email from: {}", email.getFrom());

                // Check if this is actually a new request that was misclassified
                // If the email has "In-Reply-To" header, it's definitely a reply
                if (email.getInReplyTo() == null || email.getInReplyTo().isEmpty()) {
                    log.info("Email has no In-Reply-To header, treating as new request");
                    processIncomingEmail(email);
                    return;
                }

                // It's a reply but we can't find the state - send error
                emailSenderService.sendErrorEmail(email.getFrom(),
                    "We could not find your original procurement request. Please submit a new request.",
                    email.getInReplyTo(), null);
                return;
            }

            Optional<UserContext> userContextOpt = getUserContext(email.getFrom());
            if (userContextOpt.isEmpty()) {
                log.warn("Confirmation received from unauthorized user: {}", email.getFrom());
                emailSenderService.sendErrorEmail(email.getFrom(),
                        "We couldn't find your account in the procurement system. Please register before confirming requests.",
                        email.getInReplyTo(), state != null ? state.getPoNumber() : null);
                updateState(state, STATE_USER_NOT_FOUND, null);
                humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(), email.getSubject(),"service", false,"send");
                return;
            }

            UserContext userContext = userContextOpt.get();

            // Retrieve the original procurement request data
            ProcurementRequest request = extractRequestFromState(state);
            List<Item> candidateItems = prepareCandidateItemsForConfirmation(request);

            // Parse confirmation response using Gemini
            log.debug("Step 2: Parsing confirmation response with Gemini");
            ConfirmationResponse confirmation = parseConfirmationResponse(email, request, candidateItems);

            updateState(state, STATE_CONFIRMATION_RECEIVED, null);

            if (!confirmation.isConfirmed() && isExplicitNewPurchase(confirmation.getUserReply())) {
                log.info("Confirmation email {} requested new purchase instead of inventory allocation.", email.getMessageId());
                confirmation.setConfirmed(true);
                confirmation.setFromInventory(false);
            }

                if (!confirmation.isConfirmed()) {
                    log.info("Confirmation email {} did not include approval. Requesting clarification.", email.getMessageId());
                    emailSenderService.sendErrorEmail(email.getFrom(),
                            "We couldn't determine your approval. Please reply with the Item ID or name you would like to proceed with, or say 'Confirm new purchase'.",
                            email.getInReplyTo(), state.getPoNumber());
                    return;
                }

            PurchaseOrder purchaseOrder;

            // Determine if item is from inventory or new purchase
            if (confirmation.isFromInventory()) {
                log.debug("Step 3: Processing inventory allocation");
                Item selectedItem = confirmation.getSelectedItem();

                if (selectedItem == null) {
                    log.error("Selected item is null, cannot process inventory allocation");
                    throw new IllegalStateException("No item selected for inventory allocation");
                }

                log.info("Processing inventory allocation for item: {} ({})", selectedItem.getName(), selectedItem.getId());

                inventoryService.decrementItemQuantity(selectedItem.getId(), request.getQuantity());

                // Record inventory allocation in accounting
                accountingAgentService.recordInventoryAllocation(selectedItem, userContext);
                updateState(state, STATE_ACCOUNTING_UPDATED, request);

                // Generate PO for tracking purposes
                purchaseOrder = procurementAgentService.generatePurchaseOrder(request, userContext, selectedItem);
                state.setPoNumber(purchaseOrder.getPoNumber());
                updateState(state, STATE_PO_GENERATED, request);

            } else {
                log.debug("Step 3: Processing new purchase");

                // Generate purchase order
                purchaseOrder = procurementAgentService.generatePurchaseOrder(request, userContext, null);
                state.setPoNumber(purchaseOrder.getPoNumber());
                updateState(state, STATE_PO_GENERATED, request);

                // Record purchase transaction in accounting
                accountingAgentService.recordPurchaseTransaction(purchaseOrder);
                updateState(state, STATE_ACCOUNTING_UPDATED, request);
            }

            // Send confirmation email to requester
            log.debug("Step 4: Sending confirmation email");
            emailSenderService.sendConfirmationEmail(email.getFrom(), purchaseOrder, email.getMessageId(), email.getSubject());
            updateState(state, STATE_COMPLETION_SENT, request);
            log.info("Successfully processed confirmation email. PO: {}", purchaseOrder.getPoNumber());

        } catch (Exception e) {
            log.error("Error processing confirmation email: {}", email.getMessageId(), e);
            handleGenericError(email, state, e);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Creates initial processing state for a new email.
     */
    private EmailProcessingState createInitialState(EmailMessage email) {
        EmailProcessingState state = new EmailProcessingState();
        state.setEmailMessageId(email.getMessageId());
        state.setRequesterEmail(email.getFrom());
        state.setCurrentState(STATE_EMAIL_RECEIVED);
        state.setLastUpdated(LocalDateTime.now());
        return emailProcessingStateRepository.save(state);
    }

    /**
     * Creates initial processing state with PO number.
     */
    private EmailProcessingState createInitialStateWithPoNumber(EmailMessage email, String poNumber) {
        EmailProcessingState state = new EmailProcessingState();
        state.setEmailMessageId(email.getMessageId());
        state.setRequesterEmail(email.getFrom());
        state.setCurrentState(STATE_EMAIL_RECEIVED);
        state.setPoNumber(poNumber);
        state.setLastUpdated(LocalDateTime.now());
        return emailProcessingStateRepository.save(state);
    }

    /**
     * Generates a unique PO number for tracking.
     * Format: PO-YYYY-NNNN (e.g., PO-2025-0001)
     */
    private String generatePoNumber() {
        int year = LocalDateTime.now().getYear();
        String prefix = "PO-" + year + "-";

        // Find the highest PO number for this year from email_threads table
        List<String> allPoNumbers = emailCleaningService.getAllPoNumbers();

        int maxNumber = allPoNumbers.stream()
                .filter(po -> po != null && po.startsWith(prefix))
                .map(po -> po.substring(prefix.length()))
                .mapToInt(num -> {
                    try {
                        return Integer.parseInt(num);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);

        int nextNumber = maxNumber + 1;
        String newPoNumber = String.format("PO-%d-%04d", year, nextNumber);

        log.info("Generated new PO number: {} (previous max: {})", newPoNumber, maxNumber);
        return newPoNumber;
    }

    /**
     * Updates the processing state and saves to database.
     */
    private void updateState(EmailProcessingState state, String newState, ProcurementRequest request) {
        state.setCurrentState(newState);
        state.setLastUpdated(LocalDateTime.now());

        if (request != null) {
            try {
                state.setStateData(objectMapper.writeValueAsString(request));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize request to JSON", e);
            }
        }

        emailProcessingStateRepository.save(state);
    }

    /**
     * Validates email against procurement template using AI.
     * Uses Gemini to intelligently check if all required information is present.
     */
    private TemplateValidationResult validateEmailTemplate(EmailMessage email) {
        try {
            // Use Gemini AI to validate and extract information
            return geminiAIService.validateProcurementEmail(email);
        } catch (Exception e) {
            log.error("Failed to validate email template with AI: {}", e.getMessage());
            // Fallback to simple validation if AI fails
            return new TemplateValidationResult(true, Collections.emptyList()); // Allow to proceed
        }
    }

    private TemplateValidationResult validateProcurementOrFallback(EmailMessage cleanedEmail, EmailMessage originalEmail, String poNumber) {
        TemplateValidationResult validationResult = validateEmailTemplate(cleanedEmail);
        if (!validationResult.isValid()) {
            log.warn("Email missing required fields. Prompting user for additional details.");
            emailSenderService.sendTemplateGuideEmail(originalEmail.getFrom(), validationResult.getMissingFields(),
                    originalEmail.getMessageId(), originalEmail.getSubject(), poNumber);
        }
        return validationResult;
    }

    /**
     * Public static class for template validation result.
     * Made public so it can be used by GeminiAIService.
     */
    public static class TemplateValidationResult {
        private final boolean valid;
        private final List<String> missingFields;

        public TemplateValidationResult(boolean valid, List<String> missingFields) {
            this.valid = valid;
            this.missingFields = missingFields;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }
    }

    /**
     * Classifies email with retry logic for AI failures.
     */
    private boolean classifyEmail(EmailMessage email) {
        int retryCount = 0;
        int maxRetries = 3;

        while (retryCount < maxRetries) {
            try {
                return geminiAIService.isProcurementRelated(email);
            } catch (AIAnalysisException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    throw e;
                }
                log.warn("AI classification failed, retry {}/{}", retryCount, maxRetries);
                try {
                    Thread.sleep(1000 * retryCount); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIAnalysisException("Classification interrupted", ie);
                }
            }
        }
        return false;
    }

    private boolean isExplicitNewPurchase(String reply) {
        if (reply == null || reply.isBlank()) {
            return false;
        }

        String normalized = reply.toLowerCase(Locale.ROOT);
        return normalized.contains("new purchase")
                || normalized.contains("purchase new")
                || normalized.contains("order new")
                || normalized.contains("buy new")
                || normalized.contains("none of these")
                || (normalized.contains("none") && normalized.contains("inventory"))
                || normalized.contains("proceed with new")
                || normalized.contains("no inventory")
                || normalized.contains("please purchase");
    }

    /**
     * Extracts request details from email.
     */
    private ProcurementRequest extractRequestDetails(EmailMessage email) {
        return geminiAIService.extractRequestDetails(email);
    }

    /**
     * Retrieves user context from Keycloak.
     */
    private Optional<UserContext> getUserContext(String email) {
        return keycloakIntegrationService.getUserByEmail(email);
    }

    /**
     * Sends registration request email.
     */
    private void sendRegistrationEmail(String email, String inReplyToMessageId, String poNumber) {
        try {
            emailSenderService.sendRegistrationRequestEmail(email, inReplyToMessageId, poNumber);
        } catch (Exception e) {
            log.error("Failed to send registration email to: {}", email, e);
        }
    }

    /**
     * Overloaded method for backward compatibility.
     */
    private void sendRegistrationEmail(String email) {
        sendRegistrationEmail(email, null, null);
    }

    /**
     * Checks inventory for available items.
     */
    private List<Item> checkInventory(ProcurementRequest request) {
        try {
            return inventoryService.searchAvailableItems(request.getItemType(), request.getSpecifications());
        } catch (Exception e) {
            log.warn("Inventory service error, assuming zero inventory", e);
            return Collections.emptyList();
        }
    }

    /**
     * Gets AI recommendations for items.
     */
    private List<Item> getRecommendations(ProcurementRequest request, UserContext userContext, List<Item> availableItems) {
        if (availableItems.isEmpty()) {
            return Collections.emptyList();
        }
        return geminiAIService.recommendItems(request, userContext, availableItems);
    }



    /**
     * Gets similar products using vector database semantic search.
     * Builds a search query from the request details and finds similar items.
     */
    private Map<String, Float> getSimilarProducts(ProcurementRequest request) {
        try {
            // Build a comprehensive search query from the request
            StringBuilder searchQuery = new StringBuilder();

            // Add item name or type
            if (request.getItemName() != null && !request.getItemName().isEmpty()) {
                searchQuery.append(request.getItemName());
            } else if (request.getItemType() != null) {
                searchQuery.append(request.getItemType());
            }

            // Add specifications to enrich the search
            if (request.getSpecifications() != null && !request.getSpecifications().isEmpty()) {
                for (Map.Entry<String, String> spec : request.getSpecifications().entrySet()) {
                    searchQuery.append(" ").append(spec.getValue());
                }
            }

            // Add additional notes if available
            if (request.getAdditionalNotes() != null && !request.getAdditionalNotes().isEmpty()) {
                searchQuery.append(" ").append(request.getAdditionalNotes());
            }

            String query = searchQuery.toString().trim();
            log.info("Searching for similar products with query: {}", query);

            // Search for similar items with scores (limit to top 5)
            Map<String, Float> similarItems = inventoryVectorService.searchSimilarItemsByTextWithScores(query, 5);

            log.info("Found {} similar products with scores", similarItems.size());
            return similarItems;

        } catch (Exception e) {
            log.warn("Failed to get similar products from vector search: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Sends recommendation email to requester with inventory items and similar products.
     */
    private void sendRecommendations(String email, List<Item> recommendations, Map<String, Float> similarProducts,
                                     ProcurementRequest request, String inReplyToMessageId, String originalSubject) {
        emailSenderService.sendRecommendationEmail(email, recommendations, similarProducts, request, inReplyToMessageId, originalSubject);
    }

    /**
     * Sends confirmation request for new purchase (no inventory match).
     */
    private void sendNewPurchaseConfirmation(String email, ProcurementRequest request,
                                            String inReplyToMessageId, String originalSubject) {
        emailSenderService.sendNewPurchaseConfirmationEmail(email, request, inReplyToMessageId, originalSubject);
    }

    /**
     * Parses confirmation response from email body.
     * Extracts the selected item ID and fetches the full item details from inventory.
     * Supports multiple formats: Item ID (LAP-001), Item Name (MacBook Pro), or Option number (Option 1).
     */
    private ConfirmationResponse parseConfirmationResponse(EmailMessage email, ProcurementRequest request, List<Item> candidateItems) {
        ConfirmationResponse response = new ConfirmationResponse();

        // Extract and sanitize the user's reply before sending to Gemini
        String trimmedReply = extractUserReply(email.getBody());
        String sanitizedReply = sanitizeReplyForGemini(trimmedReply);

        // Build candidate item payload (limit to small set to avoid large requests)
        List<GeminiConfirmationRequest.CandidateItem> candidatePayload = new ArrayList<>();
        if (candidateItems != null && !candidateItems.isEmpty()) {
            for (Item item : candidateItems) {
                if (item == null || item.getId() == null || item.getName() == null) {
                    continue;
                }
                candidatePayload.add(GeminiConfirmationRequest.CandidateItem.builder()
                        .itemId(item.getId())
                        .itemName(truncate(item.getName(), 80))
                        .build());
            }
        }

        GeminiConfirmationRequest confirmationRequest = GeminiConfirmationRequest.builder()
                .senderEmail(email.getFrom())
                .emailSubject(email.getSubject())
                .emailBody(sanitizedReply)
                .candidateItems(candidatePayload)
                .build();

    response.setUserReply(sanitizedReply.toLowerCase(Locale.ROOT));

        GeminiConfirmationResponse aiResponse = geminiAIService.parseConfirmationEmail(confirmationRequest);

        response.setConfirmed(aiResponse.isConfirmed());
        response.setFromInventory(false);

        List<String> matchedIds = aiResponse.getSelectedItemIds() != null
                ? aiResponse.getSelectedItemIds()
                : Collections.emptyList();

        for (String itemId : matchedIds) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            Item selectedItem = inventoryService.getItemById(itemId.trim());
            if (selectedItem != null) {
                response.setFromInventory(true);
                response.setSelectedItem(selectedItem);
                log.info("Gemini matched confirmation to inventory item: {} ({})",
                        selectedItem.getName(), selectedItem.getId());
                break;
            }
        }

        if (!response.isFromInventory()) {
            log.info("Gemini did not return a valid inventory item. Proceeding with new purchase flow.");
        }

        return response;
    }

    private String sanitizeReplyForGemini(String reply) {
        if (reply == null) {
            return "";
        }

        String sanitized = reply.trim();
        int maxLength = 1800;
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }

        return sanitized;
    }

    /**
     * Creates a copy of the email with cleaned body (no quoted replies).
     */
    private EmailMessage createCleanedEmailCopy(EmailMessage original) {
        EmailMessage cleaned = new EmailMessage();
        cleaned.setMessageId(original.getMessageId());
        cleaned.setFrom(original.getFrom());
        cleaned.setSubject(original.getSubject());
        cleaned.setBody(extractUserReply(original.getBody()));
        cleaned.setReceivedDate(original.getReceivedDate());
        cleaned.setProcessed(original.isProcessed());
        cleaned.setInReplyTo(original.getInReplyTo());
        cleaned.setReferences(original.getReferences());
        return cleaned;
    }

    /**
     * Extracts only the user's reply from an email body, removing quoted text.
     * Handles common email reply patterns like "On ... wrote:", Gmail quote blocks, etc.
     */
    private String extractUserReply(String emailBody) {
        if (emailBody == null || emailBody.isEmpty()) {
            return emailBody;
        }

        // Split by common reply delimiters
        String[] delimiters = {
            "On ",  // "On Wed, Oct 22, 2025 at 2:55 PM <email> wrote:"
            "From:",  // Outlook style
            "-----Original Message-----",  // Outlook
            "________________________________",  // Some email clients
            "<div class=\"gmail_quote",  // Gmail HTML quote
            "<blockquote",  // HTML blockquote
            "Sent from my",  // Mobile signatures
            "> ",  // Plain text quote marker
        };

        String cleanBody = emailBody;
        int earliestQuotePosition = cleanBody.length();

        // Find the earliest quote marker
        for (String delimiter : delimiters) {
            int pos = cleanBody.indexOf(delimiter);
            if (pos > 0 && pos < earliestQuotePosition) {
                earliestQuotePosition = pos;
            }
        }

        // Extract only the text before the quote
        if (earliestQuotePosition < cleanBody.length()) {
            cleanBody = cleanBody.substring(0, earliestQuotePosition);
        }

        // Clean up HTML tags if present
        cleanBody = cleanBody.replaceAll("<[^>]+>", " ");

        // Clean up extra whitespace
        cleanBody = cleanBody.trim();

        log.debug("Extracted user reply (length: {} chars): {}",
                 cleanBody.length(),
                 cleanBody.length() > 100 ? cleanBody.substring(0, 100) + "..." : cleanBody);

        return cleanBody;
    }

    /**
     * Retrieves processing state for confirmation email.
     */
    private EmailProcessingState retrieveProcessingState(EmailMessage email) {
        // Try to find by requester email and state
        List<EmailProcessingState> states = emailProcessingStateRepository.findByRequesterEmail(email.getFrom());

        return states.stream()
                .filter(s -> STATE_RECOMMENDATIONS_SENT.equals(s.getCurrentState()))
                .max(Comparator.comparing(EmailProcessingState::getLastUpdated))
                .orElse(null);
    }

    private List<Item> prepareCandidateItemsForConfirmation(ProcurementRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }

        List<String> candidateIds = request.getCandidateItemIds();
        if (candidateIds != null && !candidateIds.isEmpty()) {
            List<Item> items = inventoryService.getItemsByIds(candidateIds);
            if (!items.isEmpty()) {
                return items;
            }
        }

        return checkInventory(request);
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

    /**
     * Extracts procurement request from state data.
     */
    private ProcurementRequest extractRequestFromState(EmailProcessingState state) {
        if (state.getStateData() == null) {
            return new ProcurementRequest();
        }

        try {
            return objectMapper.readValue(state.getStateData(), ProcurementRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize request from state data", e);
            return new ProcurementRequest();
        }
    }

    // ==================== Error Handling Methods ====================

    /**
     * Handles AI analysis errors with retry logic.
     */
    private void handleAIAnalysisError(EmailMessage email, EmailProcessingState state, AIAnalysisException e) {
        log.error("AI analysis failed after retries for email: {}", email.getMessageId(), e);

        if (state != null) {
            updateState(state, STATE_ERROR, null);
        }

        try {
            String poNumber = state != null ? state.getPoNumber() : null;
            emailSenderService.sendErrorEmail(email.getFrom(),
                "We encountered an error analyzing your request. Please try again or contact support.",
                email.getMessageId(), poNumber);
        } catch (Exception emailError) {
            log.error("Failed to send error email", emailError);
        }
    }

    /**
     * Handles user not found errors.
     */
    private void handleUserNotFoundError(EmailMessage email, EmailProcessingState state) {
        log.warn("User not found: {}", email.getFrom());

        if (state != null) {
            updateState(state, STATE_USER_NOT_FOUND, null);
        }

        String poNumber = state != null ? state.getPoNumber() : null;
        sendRegistrationEmail(email.getFrom(), email.getMessageId(), poNumber);
    }

    /**
     * Handles inventory service errors by assuming zero inventory.
     */
    private void handleInventoryError(EmailMessage email, EmailProcessingState state, InventoryServiceException e) {
        log.warn("Inventory service error, proceeding with zero inventory assumption", e);

        // Continue processing with empty inventory
        try {
            if (state != null) {
                ProcurementRequest request = extractRequestFromState(state);
                UserContext userContext = getUserContext(email.getFrom()).orElseThrow();

                // Send email indicating new purchase will be initiated
                emailSenderService.sendNewPurchaseConfirmationEmail(email.getFrom(), request, email.getMessageId(), email.getSubject());
                updateState(state, STATE_RECOMMENDATIONS_SENT, request);
            }
        } catch (Exception continueError) {
            log.error("Failed to continue after inventory error", continueError);
            handleGenericError(email, state, continueError);
        }
    }

    /**
     * Handles generic errors.
     */
    private void handleGenericError(EmailMessage email, EmailProcessingState state, Exception e) {
        log.error("Generic error processing email: {}", email.getMessageId(), e);

        if (state != null) {
            updateState(state, STATE_ERROR, null);
        }

        try {
            String poNumber = state != null ? state.getPoNumber() : null;
            emailSenderService.sendErrorEmail(email.getFrom(),
                "An unexpected error occurred while processing your request. Please try again later.",
                email.getMessageId(), poNumber);
        } catch (Exception emailError) {
            log.error("Failed to send error email", emailError);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a parsed confirmation response from the requester.
     */
    private static class ConfirmationResponse {
        private boolean confirmed;
        private boolean fromInventory;
        private Item selectedItem;
        private String userReply;

        public boolean isConfirmed() {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed) {
            this.confirmed = confirmed;
        }

        public boolean isFromInventory() {
            return fromInventory;
        }

        public void setFromInventory(boolean fromInventory) {
            this.fromInventory = fromInventory;
        }

        public Item getSelectedItem() {
            return selectedItem;
        }

        public void setSelectedItem(Item selectedItem) {
            this.selectedItem = selectedItem;
        }

        public String getUserReply() {
            return userReply;
        }

        public void setUserReply(String userReply) {
            this.userReply = userReply;
        }
    }

    /**
     * Exception thrown when user is not found in Keycloak.
     */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when inventory service fails.
     */
    public static class InventoryServiceException extends RuntimeException {
        public InventoryServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
