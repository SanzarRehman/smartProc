package com.procurement.email.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final EmailSenderService emailSenderService;
    private final ProcurementAgentService procurementAgentService;
    private final AccountingAgentService accountingAgentService;
    private final EmailProcessingStateRepository emailProcessingStateRepository;
    private final ObjectMapper objectMapper;

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

        EmailProcessingState state = null;
        
        try {
            // Create initial processing state
            state = createInitialState(email);
            
            // Step 1: Classify email using Gemini AI
            log.debug("Step 1: Classifying email with AI");
            boolean isProcurementRelated = classifyEmail(email);
            
            if (!isProcurementRelated) {
                log.info("Email is not procurement-related. Marking as processed.");
                updateState(state, STATE_NOT_PROCUREMENT, null);
                return;
            }
            
            updateState(state, STATE_CLASSIFIED, null);
            
            // Step 2: Validate email format against template
            log.debug("Step 2: Validating email format against procurement template");
            // For validation and extraction, use cleaned email body (without quoted replies)
            EmailMessage cleanedEmail = createCleanedEmailCopy(email);
            TemplateValidationResult validationResult = validateEmailTemplate(cleanedEmail);
            
            if (!validationResult.isValid()) {
                log.warn("Email does not match procurement template. Sending template guide.");
                emailSenderService.sendTemplateGuideEmail(email.getFrom(), validationResult.getMissingFields(), 
                        email.getMessageId(), email.getSubject());
                updateState(state, "TEMPLATE_INVALID", null);
                return;
            }
            
            // Step 3: Extract request details
            log.debug("Step 3: Extracting procurement request details");
            ProcurementRequest request = extractRequestDetails(cleanedEmail);
            state.setProcurementRequestId(request.getRequestId());
            updateState(state, STATE_CLASSIFIED, request);
            
            // Step 3: Get user context from Keycloak
            log.debug("Step 3: Retrieving user context from Keycloak");
            Optional<UserContext> userContextOpt = getUserContext(email.getFrom());
            
            if (userContextOpt.isEmpty()) {
                log.warn("User not found in Keycloak: {}", email.getFrom());
                sendRegistrationEmail(email.getFrom());
                updateState(state, STATE_USER_NOT_FOUND, null);
                return;
            }
            
            UserContext userContext = userContextOpt.get();
            updateState(state, STATE_CONTEXT_GATHERED, request);
            
            // Step 4: Check inventory for available items
            log.debug("Step 4: Checking inventory for available items");
            List<Item> availableItems = checkInventory(request);
            updateState(state, STATE_INVENTORY_CHECKED, request);
            
            // Step 5: Get AI recommendations (always, even if inventory is empty)
            log.debug("Step 5: Getting AI recommendations for items");
            List<Item> recommendedItems = getRecommendations(request, userContext, availableItems);
            
            // Step 6: Send recommendation email with available options
            log.debug("Step 6: Sending recommendation email");
            if (!availableItems.isEmpty()) {
                // We have inventory items - send them for user to choose
                sendRecommendations(email.getFrom(), recommendedItems, request, email.getMessageId(), email.getSubject());
            } else {
                // No inventory - will need to purchase new, but still ask for confirmation
                sendNewPurchaseConfirmation(email.getFrom(), request, email.getMessageId(), email.getSubject());
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

    /**
     * Processes a confirmation email from the requester.
     * Handles the user's response to item recommendations.
     *
     * @param email the confirmation email message
     */
    @Transactional
    public void processConfirmationEmail(EmailMessage email) {
        log.info("Processing confirmation email from: {}, subject: {}", email.getFrom(), email.getSubject());

        EmailProcessingState state = null;
        
        try {
            // Parse confirmation response from email body
            log.debug("Step 1: Parsing confirmation response");
            ConfirmationResponse confirmation = parseConfirmationResponse(email);
            
            // Retrieve processing state from database
            log.debug("Step 2: Retrieving processing state");
            state = retrieveProcessingState(email, confirmation);
            
            if (state == null) {
                log.warn("No processing state found for confirmation email from: {}", email.getFrom());
                emailSenderService.sendErrorEmail(email.getFrom(), 
                    "We could not find your original procurement request. Please submit a new request.");
                return;
            }
            
            updateState(state, STATE_CONFIRMATION_RECEIVED, null);
            
            // Retrieve the original procurement request data
            ProcurementRequest request = extractRequestFromState(state);
            UserContext userContext = getUserContext(email.getFrom()).orElseThrow();
            
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
    private void sendRegistrationEmail(String email) {
        try {
            emailSenderService.sendRegistrationRequestEmail(email);
        } catch (Exception e) {
            log.error("Failed to send registration email to: {}", email, e);
        }
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
     * Sends recommendation email to requester with inventory items.
     */
    private void sendRecommendations(String email, List<Item> recommendations, ProcurementRequest request, 
                                     String inReplyToMessageId, String originalSubject) {
        emailSenderService.sendRecommendationEmail(email, recommendations, request, inReplyToMessageId, originalSubject);
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
    private ConfirmationResponse parseConfirmationResponse(EmailMessage email) {
        // Extract only the user's reply, not the quoted email chain
        String body = extractUserReply(email.getBody());
        String bodyLower = body.toLowerCase();
        
        ConfirmationResponse response = new ConfirmationResponse();
        response.setConfirmed(true);
        
        // Simple parsing logic for POC
        if (bodyLower.contains("confirm") || bodyLower.contains("yes") || bodyLower.contains("approve")) {
            response.setConfirmed(true);
        }
        
        Item selectedItem = null;
        
        // Strategy 1: Look for item ID patterns like "lap-001", "mon-002", "acc-003"
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("(lap-\\d+|mon-\\d+|acc-\\d+)", 
                                                                            java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher idMatcher = idPattern.matcher(body);
        
        if (idMatcher.find()) {
            String itemId = idMatcher.group(1).toUpperCase();
            selectedItem = inventoryService.getItemById(itemId);
            if (selectedItem != null) {
                log.info("Found item by ID: {} -> {}", itemId, selectedItem.getName());
            }
        }
        
        // Strategy 2: Look for "Option X" pattern
        if (selectedItem == null) {
            java.util.regex.Pattern optionPattern = java.util.regex.Pattern.compile("option\\s+(\\d+)", 
                                                                                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher optionMatcher = optionPattern.matcher(body);
            
            if (optionMatcher.find()) {
                int optionNumber = Integer.parseInt(optionMatcher.group(1));
                // Map option number to item ID (based on recommendation order)
                // This is a simplified approach - in production, store the mapping in state
                String[] itemIds = {"LAP-001", "LAP-002", "LAP-003"}; // Default laptop order
                if (optionNumber > 0 && optionNumber <= itemIds.length) {
                    selectedItem = inventoryService.getItemById(itemIds[optionNumber - 1]);
                    if (selectedItem != null) {
                        log.info("Found item by option number: Option {} -> {}", optionNumber, selectedItem.getName());
                    }
                }
            }
        }
        
        // Strategy 3: Look for item names (MacBook Pro, Dell XPS, ThinkPad, etc.)
        if (selectedItem == null) {
            // Check for common laptop names
            if (bodyLower.contains("macbook")) {
                selectedItem = inventoryService.getItemById("LAP-002");
                log.info("Found MacBook by name match");
            } else if (bodyLower.contains("dell") || bodyLower.contains("xps")) {
                selectedItem = inventoryService.getItemById("LAP-001");
                log.info("Found Dell XPS by name match");
            } else if (bodyLower.contains("thinkpad")) {
                selectedItem = inventoryService.getItemById("LAP-003");
                log.info("Found ThinkPad by name match");
            }
        }
        
        // Set the response
        if (selectedItem != null) {
            response.setFromInventory(true);
            response.setSelectedItem(selectedItem);
            log.info("Parsed confirmation: itemId={}, itemName={}, fromInventory=true", 
                    selectedItem.getId(), selectedItem.getName());
        } else {
            response.setFromInventory(false);
            log.info("Parsed confirmation: no matching item found, fromInventory=false (will create new purchase)");
        }
        
        return response;
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
    private EmailProcessingState retrieveProcessingState(EmailMessage email, ConfirmationResponse confirmation) {
        // Try to find by requester email and state
        List<EmailProcessingState> states = emailProcessingStateRepository.findByRequesterEmail(email.getFrom());
        
        return states.stream()
                .filter(s -> STATE_RECOMMENDATIONS_SENT.equals(s.getCurrentState()))
                .max(Comparator.comparing(EmailProcessingState::getLastUpdated))
                .orElse(null);
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
            emailSenderService.sendErrorEmail(email.getFrom(), 
                "We encountered an error analyzing your request. Please try again or contact support.");
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
        
        sendRegistrationEmail(email.getFrom());
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
            emailSenderService.sendErrorEmail(email.getFrom(), 
                "An unexpected error occurred while processing your request. Please try again later.");
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
