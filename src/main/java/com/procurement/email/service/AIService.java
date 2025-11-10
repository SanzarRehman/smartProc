package com.procurement.email.service;

import com.procurement.email.integration.dto.*;
import com.procurement.email.model.EmailMessage;
import com.procurement.email.model.Item;
import com.procurement.email.model.ProcurementRequest;
import com.procurement.email.model.UserContext;

import java.util.List;

/**
 * Interface for AI service providers (Gemini, OpenRouter, etc.)
 * Defines common methods for email analysis and processing
 */
public interface AIService {

    /**
     * Analyzes email intent to determine if it's procurement-related
     */
    GeminiEmailIntentResponse analyzeEmailIntent(EmailMessage email);

    /**
     * Validates procurement email template and extracts fields
     */
    EmailProcessingService.TemplateValidationResult validateProcurementEmail(EmailMessage email);

    /**
     * Checks if email is procurement-related
     */
    boolean isProcurementRelated(EmailMessage email);

    /**
     * Extracts procurement request details from email
     */
    ProcurementRequest extractRequestDetails(EmailMessage email);

    /**
     * Parses confirmation email
     */
    GeminiConfirmationResponse parseConfirmationEmail(GeminiConfirmationRequest request);

    /**
     * Recommends items based on request and available inventory
     */
    List<Item> recommendItems(
            ProcurementRequest request,
            UserContext userContext,
            List<Item> availableItems);

    /**
     * Matches inventory items with procurement request
     */
    GeminiInventoryMatchResponse matchInventoryItems(ProcurementRequest request, List<Item> inventoryItems);

    /**
     * Generates RRF (Request for Requisition) document
     */
    String generateRRFDocument(String prompt);
}
