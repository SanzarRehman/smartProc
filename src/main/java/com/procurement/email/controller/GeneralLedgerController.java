package com.procurement.email.controller;

import com.procurement.email.model.GeneralLedgerEntry;
import com.procurement.email.repository.GeneralLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for General Ledger operations.
 * Provides endpoints to retrieve and query GL entries.
 */
@RestController
@RequestMapping("/api/gl")
@RequiredArgsConstructor
@Slf4j
public class GeneralLedgerController {

    private final GeneralLedgerEntryRepository glRepository;

    /**
     * Get all general ledger entries.
     * 
     * @return List of all GL entries
     */
    @GetMapping
    public ResponseEntity<List<GeneralLedgerEntry>> getAllGLEntries() {
        log.info("GET /api/gl - Fetching all GL entries");
        List<GeneralLedgerEntry> entries = glRepository.findAll();
        return ResponseEntity.ok(entries);
    }

    /**
     * Get a specific GL entry by ID.
     * 
     * @param id the GL entry ID
     * @return the GL entry if found, 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<GeneralLedgerEntry> getGLEntryById(@PathVariable Long id) {
        log.info("GET /api/gl/{} - Fetching GL entry by ID", id);
        return glRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("GL entry not found: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Get GL entries by reference number (PO number or allocation ID).
     * 
     * @param refNumber the reference number (e.g., "PO-2024-001", "ALLOC-2024-001")
     * @return list of GL entries with the specified reference number
     */
    @GetMapping("/reference/{refNumber}")
    public ResponseEntity<List<GeneralLedgerEntry>> getGLEntriesByReference(@PathVariable String refNumber) {
        log.info("GET /api/gl/reference/{} - Fetching GL entries by reference number", refNumber);
        List<GeneralLedgerEntry> entries = glRepository.findByReferenceNumber(refNumber);
        return ResponseEntity.ok(entries);
    }

    /**
     * Get GL entries by requester email.
     * 
     * @param email the requester's email address
     * @return list of GL entries for the requester
     */
    @GetMapping("/requester/{email}")
    public ResponseEntity<List<GeneralLedgerEntry>> getGLEntriesByRequester(@PathVariable String email) {
        log.info("GET /api/gl/requester/{} - Fetching GL entries by requester", email);
        List<GeneralLedgerEntry> entries = glRepository.findByRequesterEmail(email);
        return ResponseEntity.ok(entries);
    }

    /**
     * Get GL entries by transaction type.
     * 
     * @param type the transaction type (e.g., "ALLOCATION", "PURCHASE")
     * @return list of GL entries with the specified transaction type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<GeneralLedgerEntry>> getGLEntriesByType(@PathVariable String type) {
        log.info("GET /api/gl/type/{} - Fetching GL entries by transaction type", type);
        List<GeneralLedgerEntry> entries = glRepository.findByTransactionType(type.toUpperCase());
        return ResponseEntity.ok(entries);
    }

    /**
     * Get GL entries within a date range.
     * 
     * @param startDate start date (format: yyyy-MM-dd'T'HH:mm:ss)
     * @param endDate end date (format: yyyy-MM-dd'T'HH:mm:ss)
     * @return list of GL entries within the date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<GeneralLedgerEntry>> getGLEntriesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("GET /api/gl/date-range - Fetching GL entries from {} to {}", startDate, endDate);
        List<GeneralLedgerEntry> entries = glRepository.findByTransactionDateBetween(startDate, endDate);
        return ResponseEntity.ok(entries);
    }

    /**
     * Get recent GL entries (last N days).
     * 
     * @param days number of days to look back (default: 30)
     * @return list of recent GL entries
     */
    @GetMapping("/recent")
    public ResponseEntity<List<GeneralLedgerEntry>> getRecentGLEntries(
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/gl/recent - Fetching GL entries from last {} days", days);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        List<GeneralLedgerEntry> entries = glRepository.findByTransactionDateBetween(cutoffDate, LocalDateTime.now());
        return ResponseEntity.ok(entries);
    }

    /**
     * Get GL statistics.
     * 
     * @return statistics including total entries, total debits, total credits, etc.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGLStats() {
        log.info("GET /api/gl/stats - Fetching GL statistics");
        List<GeneralLedgerEntry> allEntries = glRepository.findAll();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", allEntries.size());
        
        BigDecimal totalAmount = allEntries.stream()
                .map(GeneralLedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalAmount", totalAmount);
        
        // Count by transaction type
        Map<String, Long> byType = allEntries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GeneralLedgerEntry::getTransactionType, 
                        java.util.stream.Collectors.counting()));
        stats.put("entriesByType", byType);
        
        // Sum by transaction type
        Map<String, BigDecimal> amountByType = allEntries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GeneralLedgerEntry::getTransactionType,
                        java.util.stream.Collectors.reducing(
                                BigDecimal.ZERO,
                                GeneralLedgerEntry::getAmount,
                                BigDecimal::add)));
        stats.put("amountByType", amountByType);
        
        return ResponseEntity.ok(stats);
    }
}
