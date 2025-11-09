package com.procurement.email.controller;

import com.procurement.email.model.RequestForRequisition;
import com.procurement.email.service.PDFGenerationService;
import com.procurement.email.service.RRFService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST Controller for Request for Requisition (RRF) documents.
 */
@RestController
@RequestMapping("/api/rrf")
@RequiredArgsConstructor
@Slf4j
public class RRFController {
    
    private final RRFService rrfService;
    private final PDFGenerationService pdfGenerationService;
    
    /**
     * Get RRF document by PO number.
     * 
     * Example: GET /api/rrf/PO-2025-0001
     */
    @GetMapping("/{poNumber}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRRFByPoNumber(@PathVariable String poNumber) {
        log.info("Fetching RRF for PO: {}", poNumber);
        
        Optional<RequestForRequisition> rrfOpt = rrfService.getRRFByPoNumber(poNumber);
        
        if (rrfOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("RRF not found for PO: " + poNumber));
        }
        
        return ResponseEntity.ok(rrfOpt.get());
    }
    
    /**
     * Get RRF document content as plain text (for preview).
     * 
     * Example: GET /api/rrf/PO-2025-0001/content
     */
    @GetMapping("/{poNumber}/content")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRRFContent(@PathVariable String poNumber) {
        log.info("Fetching RRF content for PO: {}", poNumber);
        
        Optional<RequestForRequisition> rrfOpt = rrfService.getRRFByPoNumber(poNumber);
        
        if (rrfOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("RRF not found for PO: " + poNumber));
        }
        
        RequestForRequisition rrf = rrfOpt.get();
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(rrf.getRrfContent());
    }
    
    /**
     * Get RRF document as HTML (for preview in browser).
     * 
     * Example: GET /api/rrf/PO-2025-0001/html
     */
    @GetMapping("/{poNumber}/html")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getRRFAsHtml(@PathVariable String poNumber) {
        log.info("Fetching RRF HTML for PO: {}", poNumber);
        
        Optional<RequestForRequisition> rrfOpt = rrfService.getRRFByPoNumber(poNumber);
        
        if (rrfOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("RRF not found for PO: " + poNumber));
        }
        
        RequestForRequisition rrf = rrfOpt.get();
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(rrf.getRrfContent());
    }
    
    /**
     * Download RRF document as PDF.
     * 
     * Example: GET /api/rrf/PO-2025-0001/pdf
     */
    @GetMapping("/{poNumber}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadRRFAsPDF(@PathVariable String poNumber) {
        log.info("Generating PDF for RRF: {}", poNumber);
        
        Optional<RequestForRequisition> rrfOpt = rrfService.getRRFByPoNumber(poNumber);
        
        if (rrfOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("RRF not found for PO: " + poNumber));
        }
        
        try {
            RequestForRequisition rrf = rrfOpt.get();
            byte[] pdfBytes = pdfGenerationService.generateRRFPDF(rrf);
            String filename = "RRF_" + poNumber + ".pdf";
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Failed to generate PDF for RRF: {}", poNumber, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to generate PDF: " + e.getMessage()));
        }
    }
    
    /**
     * Download RRF document as text file.
     * 
     * Example: GET /api/rrf/PO-2025-0001/download
     */
    @GetMapping("/{poNumber}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<?> downloadRRF(@PathVariable String poNumber) {
        log.info("Downloading RRF for PO: {}", poNumber);
        
        Optional<RequestForRequisition> rrfOpt = rrfService.getRRFByPoNumber(poNumber);
        
        if (rrfOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("RRF not found for PO: " + poNumber));
        }
        
        RequestForRequisition rrf = rrfOpt.get();
        String filename = "RRF_" + poNumber + ".txt";
        
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(rrf.getRrfContent());
    }
    
    /**
     * Update RRF status.
     * 
     * Example: PUT /api/rrf/PO-2025-0001/status?status=APPROVED
     */
    @PutMapping("/{poNumber}/status")
    public ResponseEntity<?> updateRRFStatus(
            @PathVariable String poNumber,
            @RequestParam String status) {
        log.info("Updating RRF status for PO: {} to {}", poNumber, status);
        
        try {
            rrfService.updateRRFStatus(poNumber, status);
            return ResponseEntity.ok(new SuccessResponse("RRF status updated successfully"));
        } catch (Exception e) {
            log.error("Failed to update RRF status for PO: {}", poNumber, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update RRF status: " + e.getMessage()));
        }
    }
    
    /**
     * Error response DTO.
     */
    private record ErrorResponse(String error) {}
    
    /**
     * Success response DTO.
     */
    private record SuccessResponse(String message) {}
}
