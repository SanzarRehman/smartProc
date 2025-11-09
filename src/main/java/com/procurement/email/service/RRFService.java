package com.procurement.email.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.email.model.Item;
import com.procurement.email.model.ProcurementRequest;
import com.procurement.email.model.RequestForRequisition;
import com.procurement.email.model.UserContext;
import com.procurement.email.repository.RequestForRequisitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Service for generating and managing Request for Requisition (RRF) documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RRFService {
    
    private final RequestForRequisitionRepository rrfRepository;
    private final GeminiAIService aiService;
    private final ObjectMapper objectMapper;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
    
    /**
     * Generates an RRF document using LLM based on procurement request details.
     */
    @Transactional
    public RequestForRequisition generateAndSaveRRF(
            String poNumber,
            ProcurementRequest request,
            UserContext userContext,
            BigDecimal estimatedAmount
    ) {
        return generateAndSaveRRF(poNumber, request, userContext, estimatedAmount, null);
    }
    
    /**
     * Generates an RRF document with specific selected item information.
     */
    @Transactional
    public RequestForRequisition generateAndSaveRRF(
            String poNumber,
            ProcurementRequest request,
            UserContext userContext,
            BigDecimal estimatedAmount,
            Item selectedItem
    ) {
        log.info("Generating RRF document for PO: {}", poNumber);
        
        try {
            // Check if RRF already exists
            Optional<RequestForRequisition> existing = rrfRepository.findByPoNumber(poNumber);
            if (existing.isPresent()) {
                log.info("RRF already exists for PO: {}", poNumber);
                return existing.get();
            }
            
            // Generate RRF content using LLM
            String rrfContent = generateRRFWithLLM(poNumber, request, userContext, estimatedAmount, selectedItem);
            
            // Create and save RRF entity
            RequestForRequisition rrf = new RequestForRequisition();
            rrf.setPoNumber(poNumber);
            rrf.setRequesterEmail(userContext.getEmail());
            rrf.setRequesterName(userContext.getUsername());
            
            // Use selected item name if available, otherwise fallback to request
            if (selectedItem != null) {
                rrf.setItemName(selectedItem.getName());
                rrf.setItemType(selectedItem.getType());
            } else {
                rrf.setItemName(request.getItemName() != null ? request.getItemName() : request.getItemType());
                rrf.setItemType(request.getItemType());
            }
            
            rrf.setQuantity(request.getQuantity());
            rrf.setEstimatedAmount(estimatedAmount);
            rrf.setRrfContent(rrfContent);
            rrf.setStatus("PENDING_APPROVAL");
            
            // Convert specifications to JSON string
            if (request.getSpecifications() != null) {
                rrf.setSpecifications(objectMapper.writeValueAsString(request.getSpecifications()));
            }
            
            rrf.setAdditionalNotes(request.getAdditionalNotes());
            
            RequestForRequisition saved = rrfRepository.save(rrf);
            log.info("RRF document saved successfully for PO: {}", poNumber);
            
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to generate RRF for PO: {}", poNumber, e);
            throw new RRFGenerationException("Failed to generate RRF document", e);
        }
    }
    
    /**
     * Generates the RRF content using LLM.
     */
    private String generateRRFWithLLM(
            String poNumber,
            ProcurementRequest request,
            UserContext userContext,
            BigDecimal estimatedAmount,
            Item selectedItem
    ) {
        log.info("Generating RRF content using LLM for PO: {}", poNumber);
        
        // Build the prompt for LLM
        String prompt = buildRRFPrompt(poNumber, request, userContext, estimatedAmount, selectedItem);
        
        // Call LLM to generate formatted RRF
        String rrfContent = aiService.generateRRFDocument(prompt);
        
        return rrfContent;
    }
    
    /**
     * Builds the prompt for LLM to generate RRF document.
     */
    private String buildRRFPrompt(
            String poNumber,
            ProcurementRequest request,
            UserContext userContext,
            BigDecimal estimatedAmount,
            Item selectedItem
    ) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Generate a professional Request for Requisition (RRF) document with the following details:\n\n");
        prompt.append("Document Type: REQUEST FOR REQUISITION\n");
        prompt.append("RRF Number: ").append(poNumber).append("\n");
        prompt.append("Date: ").append(LocalDateTime.now().format(DATE_FORMATTER)).append("\n\n");
        
        prompt.append("REQUESTER INFORMATION:\n");
        prompt.append("Name: ").append(userContext.getUsername()).append("\n");
        prompt.append("Email: ").append(userContext.getEmail()).append("\n");
     //   prompt.append("Department: ").append(userContext.getDepartment() != null ? userContext.getDepartment() : "N/A").append("\n");
        prompt.append("Role: ").append(userContext.getRole()).append("\n\n");
        
        prompt.append("ITEM DETAILS:\n");
        
        // Use selected item if available, otherwise use request data
        if (selectedItem != null) {
            prompt.append("Item Name: ").append(selectedItem.getName()).append("\n");
            prompt.append("Item ID: ").append(selectedItem.getId()).append("\n");
            prompt.append("Category: ").append(selectedItem.getType()).append("\n");
           // prompt.append("Description: ").append(selectedItem.getDescription() != null ? selectedItem.getDescription() : "N/A").append("\n");
        } else {
            prompt.append("Item Name: ").append(request.getItemName() != null ? request.getItemName() : request.getItemType()).append("\n");
            prompt.append("Category: ").append(request.getItemType()).append("\n");
        }
        
        prompt.append("Quantity: ").append(request.getQuantity()).append(" unit(s)\n");
        
        if (request.getSpecifications() != null && !request.getSpecifications().isEmpty()) {
            prompt.append("\nSpecifications:\n");
            for (Map.Entry<String, String> spec : request.getSpecifications().entrySet()) {
                prompt.append("- ").append(formatSpecKey(spec.getKey())).append(": ").append(spec.getValue()).append("\n");
            }
        }
        
        prompt.append("\nEstimated Cost: $").append(estimatedAmount).append("\n");
        
        if (request.getAdditionalNotes() != null && !request.getAdditionalNotes().isEmpty()) {
            prompt.append("\nAdditional Requirements:\n").append(request.getAdditionalNotes()).append("\n");
        }
        
        prompt.append("\n\n=== IMPORTANT: Generate the document in HTML format ===");
        prompt.append("\n\nPlease format this as a formal RRF document in HTML with:");
        prompt.append("\n1. Professional header with company letterhead style using <div> and <h1> tags");
        prompt.append("\n2. Clear sections for requester info, item details, justification, and approvals using <section> or <div> tags");
        prompt.append("\n3. A business justification section explaining why this purchase is necessary");
        prompt.append("\n4. Use HTML tables (<table>) for structured data like requester info and item details");
        prompt.append("\n5. Use proper HTML styling with inline CSS for colors, fonts, and spacing");
        prompt.append("\n6. Include signature blocks formatted as a table with three columns (Requester, Department Head, Finance)");
        prompt.append("\n7. Add a terms and conditions section at the bottom");
        prompt.append("\n8. Use professional color scheme (e.g., blue headers, gray backgrounds for tables)");
        prompt.append("\n9. Make it responsive and print-ready");
        prompt.append("\n\nGenerate ONLY the complete HTML content starting with <html> and ending with </html>.");
        prompt.append("\nInclude proper <head> with <style> tags for CSS and <body> with all content.");
        prompt.append("\nDo NOT include markdown code blocks or any text outside the HTML.");
        prompt.append("\n\nIMPORTANT: Generate the RRF for ONLY the ONE selected/confirmed item above. Do NOT include multiple items or options.");
        
        return prompt.toString();
    }
    
    /**
     * Formats specification keys to be more readable.
     */
    private String formatSpecKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        
        // Handle common abbreviations
        if (key.equalsIgnoreCase("ram") || key.equalsIgnoreCase("cpu") || 
            key.equalsIgnoreCase("gpu") || key.equalsIgnoreCase("ssd")) {
            return key.toUpperCase();
        }
        
        // Replace underscores with spaces and capitalize words
        String[] words = key.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Retrieves RRF by PO number.
     */
    public Optional<RequestForRequisition> getRRFByPoNumber(String poNumber) {
        return rrfRepository.findByPoNumber(poNumber);
    }
    
    /**
     * Updates RRF status.
     */
    @Transactional
    public void updateRRFStatus(String poNumber, String status) {
        Optional<RequestForRequisition> rrfOpt = rrfRepository.findByPoNumber(poNumber);
        if (rrfOpt.isPresent()) {
            RequestForRequisition rrf = rrfOpt.get();
            rrf.setStatus(status);
            rrfRepository.save(rrf);
            log.info("Updated RRF status to {} for PO: {}", status, poNumber);
        } else {
            log.warn("RRF not found for PO: {}", poNumber);
        }
    }
    
    /**
     * Custom exception for RRF generation failures.
     */
    public static class RRFGenerationException extends RuntimeException {
        public RRFGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
