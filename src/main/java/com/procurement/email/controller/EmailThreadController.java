package com.procurement.email.controller;

import com.procurement.email.model.EmailThread;
import com.procurement.email.repository.EmailThreadRepository;
import com.procurement.email.service.EmailCleaningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for email thread operations.
 * Provides endpoints to retrieve email conversations for UI display.
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Slf4j
public class EmailThreadController {

    private final EmailThreadRepository emailThreadRepository;
    private final EmailCleaningService emailCleaningService;

    /**
     * Get all email threads by PO number (flat list, ordered by received date).
     * 
     * @param poNumber the PO number
     * @return list of all emails in the thread
     */
    @GetMapping("/po/{poNumber}")
    public ResponseEntity<List<EmailThread>> getEmailsByPoNumber(@PathVariable String poNumber) {
        log.info("GET /api/emails/po/{} - Fetching emails by PO number", poNumber);
        List<EmailThread> threads = emailThreadRepository.findByPoNumberOrderByReceivedAtAsc(poNumber);
        return ResponseEntity.ok(threads);
    }

    /**
     * Get email thread hierarchy by PO number (nested structure).
     * Returns parent-child relationships for UI display.
     * 
     * @param poNumber the PO number
     * @return hierarchical email thread structure
     */
    @GetMapping("/po/{poNumber}/hierarchy")
    public ResponseEntity<List<EmailCleaningService.EmailThreadNode>> getEmailThreadHierarchy(
            @PathVariable String poNumber) {
        log.info("GET /api/emails/po/{}/hierarchy - Fetching email thread hierarchy", poNumber);
        List<EmailCleaningService.EmailThreadNode> hierarchy = 
                emailCleaningService.getEmailThreadHierarchy(poNumber);
        return ResponseEntity.ok(hierarchy);
    }

    /**
     * Get a specific email by message ID.
     * 
     * @param messageId the email message ID
     * @return the email thread if found, 404 otherwise
     */
    @GetMapping("/message/{messageId}")
    public ResponseEntity<EmailThread> getEmailByMessageId(@PathVariable String messageId) {
        log.info("GET /api/emails/message/{} - Fetching email by message ID", messageId);
        return emailThreadRepository.findByMessageId(messageId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Email not found: {}", messageId);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Get all emails from a specific sender.
     * 
     * @param email the sender's email address
     * @return list of emails from the sender
     */
    @GetMapping("/sender/{email}")
    public ResponseEntity<List<EmailThread>> getEmailsBySender(@PathVariable String email) {
        log.info("GET /api/emails/sender/{} - Fetching emails by sender", email);
        List<EmailThread> threads = emailThreadRepository.findByFromEmailOrderByReceivedAtDesc(email);
        return ResponseEntity.ok(threads);
    }

    /**
     * Get all procurement-related emails.
     * 
     * @return list of procurement emails
     */
    @GetMapping("/procurement")
    public ResponseEntity<List<EmailThread>> getProcurementEmails() {
        log.info("GET /api/emails/procurement - Fetching procurement-related emails");
        List<EmailThread> threads = emailThreadRepository.findByProcurementRelatedTrueOrderByReceivedAtDesc();
        return ResponseEntity.ok(threads);
    }

    /**
     * Get emails by processing state.
     * 
     * @param state the processing state (e.g., EMAIL_RECEIVED, CLASSIFIED, etc.)
     * @return list of emails with the specified state
     */
    @GetMapping("/state/{state}")
    public ResponseEntity<List<EmailThread>> getEmailsByState(@PathVariable String state) {
        log.info("GET /api/emails/state/{} - Fetching emails by processing state", state);
        List<EmailThread> threads = emailThreadRepository.findByProcessingStateOrderByReceivedAtDesc(state);
        return ResponseEntity.ok(threads);
    }

    /**
     * Get recent emails (last N days).
     * 
     * @param days number of days to look back (default: 7)
     * @return list of recent emails
     */
    @GetMapping("/recent")
    public ResponseEntity<List<EmailThread>> getRecentEmails(
            @RequestParam(defaultValue = "7") int days) {
        log.info("GET /api/emails/recent - Fetching emails from last {} days", days);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        List<EmailThread> threads = emailThreadRepository.findRecentEmails(cutoffDate);
        return ResponseEntity.ok(threads);
    }

    /**
     * Get all unique PO numbers.
     * Useful for listing all email threads/conversations.
     * 
     * @return list of PO numbers
     */
    @GetMapping("/po-numbers")
    public ResponseEntity<List<String>> getAllPoNumbers() {
        log.info("GET /api/emails/po-numbers - Fetching all PO numbers");
        List<String> poNumbers = emailThreadRepository.findAllDistinctPoNumbers();
        return ResponseEntity.ok(poNumbers);
    }

    /**
     * Get email thread statistics for a PO number.
     * 
     * @param poNumber the PO number
     * @return statistics including email count, participants, etc.
     */
    @GetMapping("/po/{poNumber}/stats")
    public ResponseEntity<Map<String, Object>> getThreadStats(@PathVariable String poNumber) {
        log.info("GET /api/emails/po/{}/stats - Fetching thread statistics", poNumber);
        
        List<EmailThread> threads = emailThreadRepository.findByPoNumberOrderByReceivedAtAsc(poNumber);
        
        if (threads.isEmpty()) {
            log.warn("No emails found for PO: {}", poNumber);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEmails", threads.size());
        stats.put("procurementEmails", threads.stream().filter(EmailThread::isProcurementRelated).count());
        stats.put("systemEmails", threads.stream().filter(EmailThread::isSystemMessage).count());
        
        // Unique participants
        List<String> participants = threads.stream()
                .map(EmailThread::getFromEmail)
                .distinct()
                .toList();
        stats.put("participants", participants);
        stats.put("participantCount", participants.size());
        
        // Date range
        if (!threads.isEmpty()) {
            stats.put("firstEmail", threads.get(0).getReceivedAt());
            stats.put("lastEmail", threads.get(threads.size() - 1).getReceivedAt());
        }
        
        // Processing states
        Map<String, Long> stateBreakdown = threads.stream()
                .filter(t -> t.getProcessingState() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        EmailThread::getProcessingState,
                        java.util.stream.Collectors.counting()));
        stats.put("stateBreakdown", stateBreakdown);
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Search emails by subject or body content.
     * 
     * @param query search query
     * @return list of matching emails
     */
    @GetMapping("/search")
    public ResponseEntity<List<EmailThread>> searchEmails(@RequestParam String query) {
        log.info("GET /api/emails/search - Searching emails with query: {}", query);
        
        List<EmailThread> allThreads = emailThreadRepository.findAll();
        String lowerQuery = query.toLowerCase();
        
        List<EmailThread> matches = allThreads.stream()
                .filter(thread -> 
                    (thread.getSubject() != null && thread.getSubject().toLowerCase().contains(lowerQuery)) ||
                    (thread.getCleanedBody() != null && thread.getCleanedBody().toLowerCase().contains(lowerQuery))
                )
                .sorted((t1, t2) -> t2.getReceivedAt().compareTo(t1.getReceivedAt()))
                .toList();
        
        log.info("Found {} matching emails", matches.size());
        return ResponseEntity.ok(matches);
    }
}
