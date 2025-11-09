package com.procurement.email.service;

import com.procurement.email.model.EmailMessage;
import com.procurement.email.model.EmailThread;
import com.procurement.email.repository.EmailThreadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for cleaning and parsing email content.
 * Extracts nested email chains, removes HTML/signatures, and builds hierarchical structure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailCleaningService {

    private final EmailThreadRepository emailThreadRepository;
    
    // Common email signature patterns
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(
            "(?i)(^--\\s*$|^__|^_\\s*$|^thanks,?|^regards,?|^best,?|^sincerely,?|^cheers,?|" +
            "sent from my|get outlook for|sent from outlook|sent from Mail for Windows)",
            Pattern.MULTILINE
    );
    
    // Email reply header patterns (e.g., "On Mon, Nov 5, 2024 at 10:30 AM, John <john@example.com> wrote:")
    private static final Pattern REPLY_HEADER_PATTERN = Pattern.compile(
            "(?i)(^On\\s+.*wrote:|^From:.*Sent:.*To:.*Subject:|^>+\\s*On\\s+.*wrote:)",
            Pattern.MULTILINE
    );
    
    // Quoted text patterns
    private static final Pattern QUOTED_TEXT_PATTERN = Pattern.compile(
            "^>+.*$",
            Pattern.MULTILINE
    );
    
    /**
     * Parse and save email with cleaned content.
     * 
     * @param email the incoming email
     * @param poNumber the PO number to link this email to
     * @param isProcurementRelated whether this is procurement-related
     * @param processingState the current processing state
     * @return the saved EmailThread entity
     */
    @Transactional
    public EmailThread saveEmailThread(EmailMessage email, String poNumber, 
                                       boolean isProcurementRelated, String processingState) {
        log.info("Saving email thread: messageId={}, poNumber={}", email.getMessageId(), poNumber);
        
        // Check if already exists
        Optional<EmailThread> existing = emailThreadRepository.findByMessageId(email.getMessageId());
        if (existing.isPresent()) {
            log.debug("Email thread already exists for messageId: {}", email.getMessageId());
            return existing.get();
        }
        
        EmailThread thread = new EmailThread();
        thread.setPoNumber(poNumber);
        thread.setMessageId(email.getMessageId());
        thread.setParentMessageId(email.getInReplyTo());
        thread.setFromEmail(email.getFrom());
        thread.setToEmail("procurement@company.com"); // Default, can be extracted from email headers
        thread.setSubject(email.getSubject());
        thread.setRawContent(email.getBody());
        thread.setCleanedBody(cleanEmailBody(email.getBody()));
        thread.setSystemMessage(false);
        thread.setProcurementRelated(isProcurementRelated);
        thread.setProcessingState(processingState);
        thread.setReceivedAt(email.getReceivedDate() != null ? email.getReceivedDate() : LocalDateTime.now());
        thread.setDepthLevel(calculateDepthLevel(email.getInReplyTo()));
        
        EmailThread saved = emailThreadRepository.save(thread);
        log.info("Saved email thread with ID: {}", saved.getId());
        
        return saved;
    }
    
    /**
     * Save a system-generated email (like recommendations or confirmations).
     */
    @Transactional
    public EmailThread saveSystemEmail(String poNumber, String toEmail, String subject, 
                                       String body, String processingState) {
        return saveSystemEmail(poNumber, toEmail, subject, body, processingState, null);
    }
    
    /**
     * Save system email with specific message ID (for sent emails).
     */
    public EmailThread saveSystemEmail(String poNumber, String toEmail, String subject, 
                                       String body, String processingState, String messageId) {
        return saveSystemEmail(poNumber, toEmail, subject, body, processingState, messageId, null);
    }
    
    /**
     * Save system email with specific message ID and parent message ID.
     * This is the full version used when sending system replies.
     */
    public EmailThread saveSystemEmail(String poNumber, String toEmail, String subject, 
                                       String body, String processingState, String messageId, 
                                       String parentMessageId) {
        log.info("Saving system email: poNumber={}, to={}, messageId={}, parentId={}", 
                poNumber, toEmail, messageId, parentMessageId);
        
        // Use provided message ID or generate one
        if (messageId == null || messageId.isEmpty()) {
            messageId = generateSystemMessageId(poNumber);
        }
        
        // Check if already exists
        Optional<EmailThread> existing = emailThreadRepository.findByMessageId(messageId);
        if (existing.isPresent()) {
            log.debug("System email already exists for messageId: {}", messageId);
            return existing.get();
        }
        
        // Use provided parent message ID, or find the latest
        String actualParentMessageId = parentMessageId;
        if (actualParentMessageId == null || actualParentMessageId.isEmpty()) {
            actualParentMessageId = findLatestMessageIdByPoNumber(poNumber);
        }
        
        EmailThread thread = new EmailThread();
        thread.setPoNumber(poNumber);
        thread.setMessageId(messageId);
        thread.setParentMessageId(actualParentMessageId);
        thread.setFromEmail("procurement@company.com");
        thread.setToEmail(toEmail);
        thread.setSubject(subject);
        thread.setRawContent(body);
        thread.setCleanedBody(cleanEmailBody(body)); // Clean HTML from system emails
        thread.setSystemMessage(true);
        thread.setProcurementRelated(true);
        thread.setProcessingState(processingState);
        thread.setReceivedAt(LocalDateTime.now());
        thread.setDepthLevel(calculateDepthLevel(actualParentMessageId));
        
        EmailThread saved = emailThreadRepository.save(thread);
        log.info("Saved system email with ID: {}, depth: {}, parent: {}", 
                saved.getId(), saved.getDepthLevel(), actualParentMessageId);
        
        return saved;
    }
    
    /**
     * Clean email body by removing HTML, signatures, and quoted text.
     */
    public String cleanEmailBody(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return "";
        }
        
        // Step 1: Remove HTML tags and convert to plain text
        String plainText = htmlToPlainText(rawBody);
        
        // Step 2: Remove email reply headers
        plainText = removeReplyHeaders(plainText);
        
        // Step 3: Remove quoted text (lines starting with >)
        plainText = removeQuotedText(plainText);
        
        // Step 4: Remove signature
        plainText = removeSignature(plainText);
        
        // Step 5: Clean up whitespace
        plainText = cleanWhitespace(plainText);
        
        log.debug("Cleaned email body: {} chars -> {} chars", rawBody.length(), plainText.length());
        
        return plainText.trim();
    }
    
    /**
     * Convert HTML email to plain text.
     */
    private String htmlToPlainText(String html) {
        if (!html.contains("<")) {
            return html; // Already plain text
        }
        
        try {
            // Parse HTML and extract text
            Document doc = Jsoup.parse(html);
            
            // Remove style, script, and hidden elements
            doc.select("style, script, [style*='display: none'], [style*='display:none']").remove();
            
            // Get text with line breaks preserved
            String text = doc.body().text();
            
            return text;
        } catch (Exception e) {
            log.warn("Error parsing HTML, returning raw content: {}", e.getMessage());
            return Jsoup.clean(html, Safelist.none());
        }
    }
    
    /**
     * Remove email reply headers (e.g., "On Mon, Nov 5 at 10:30 AM, John wrote:").
     */
    private String removeReplyHeaders(String text) {
        Matcher matcher = REPLY_HEADER_PATTERN.matcher(text);
        return matcher.replaceAll("");
    }
    
    /**
     * Remove quoted text (lines starting with >).
     */
    private String removeQuotedText(String text) {
        return Arrays.stream(text.split("\n"))
                .filter(line -> !line.trim().startsWith(">"))
                .collect(Collectors.joining("\n"));
    }
    
    /**
     * Remove email signature.
     */
    private String removeSignature(String text) {
        Matcher matcher = SIGNATURE_PATTERN.matcher(text);
        if (matcher.find()) {
            int signatureStart = matcher.start();
            return text.substring(0, signatureStart).trim();
        }
        return text;
    }
    
    /**
     * Clean up excessive whitespace.
     */
    private String cleanWhitespace(String text) {
        // Replace multiple newlines with double newline
        text = text.replaceAll("\n{3,}", "\n\n");
        
        // Replace multiple spaces with single space
        text = text.replaceAll("[ \\t]{2,}", " ");
        
        // Trim each line
        return Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }
    
    /**
     * Calculate thread depth level based on parent message.
     */
    private int calculateDepthLevel(String parentMessageId) {
        if (parentMessageId == null || parentMessageId.isEmpty()) {
            return 0; // Root level
        }
        
        Optional<EmailThread> parent = emailThreadRepository.findByMessageId(parentMessageId);
        if (parent.isPresent()) {
            return parent.get().getDepthLevel() + 1;
        }
        
        return 1; // Parent not found, assume first reply
    }
    
    /**
     * Find the latest message ID for a PO number (for threading system emails).
     */
    private String findLatestMessageIdByPoNumber(String poNumber) {
        List<EmailThread> threads = emailThreadRepository.findByPoNumberOrderByReceivedAtAsc(poNumber);
        if (threads.isEmpty()) {
            return null;
        }
        return threads.get(threads.size() - 1).getMessageId();
    }
    
    /**
     * Generate a unique message ID for system emails.
     */
    private String generateSystemMessageId(String poNumber) {
        return String.format("<system-%s-%s@procurement.company.com>", 
                poNumber, 
                System.currentTimeMillis());
    }
    
    /**
     * Get entire email thread hierarchy for a PO number.
     * Returns nested structure with parent-child relationships.
     */
    public List<EmailThreadNode> getEmailThreadHierarchy(String poNumber) {
        log.info("Building email thread hierarchy for PO: {}", poNumber);
        
        List<EmailThread> allThreads = emailThreadRepository.findByPoNumberOrderByReceivedAtAsc(poNumber);
        
        if (allThreads.isEmpty()) {
            log.debug("No email threads found for PO: {}", poNumber);
            return Collections.emptyList();
        }
        
        // Build map of message ID to thread
        Map<String, EmailThread> threadMap = allThreads.stream()
                .collect(Collectors.toMap(EmailThread::getMessageId, t -> t));
        
        // Find root threads (no parent)
        List<EmailThread> roots = allThreads.stream()
                .filter(t -> t.getParentMessageId() == null || 
                            !threadMap.containsKey(t.getParentMessageId()))
                .collect(Collectors.toList());
        
        // Build hierarchy recursively
        List<EmailThreadNode> hierarchy = roots.stream()
                .map(root -> buildThreadNode(root, threadMap))
                .collect(Collectors.toList());
        
        log.info("Built hierarchy with {} root threads", hierarchy.size());
        
        return hierarchy;
    }
    
    /**
     * Recursively build thread node with children.
     */
    private EmailThreadNode buildThreadNode(EmailThread thread, Map<String, EmailThread> threadMap) {
        EmailThreadNode node = new EmailThreadNode(thread);
        
        // Find children
        List<EmailThread> children = threadMap.values().stream()
                .filter(t -> thread.getMessageId().equals(t.getParentMessageId()))
                .sorted(Comparator.comparing(EmailThread::getReceivedAt))
                .collect(Collectors.toList());
        
        // Recursively build child nodes
        node.setChildren(children.stream()
                .map(child -> buildThreadNode(child, threadMap))
                .collect(Collectors.toList()));
        
        return node;
    }
    
    /**
     * Get all unique PO numbers from email threads.
     * Used for generating new unique PO numbers.
     */
    public List<String> getAllPoNumbers() {
        return emailThreadRepository.findAll().stream()
                .map(EmailThread::getPoNumber)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Check if an email already exists in the database, and return its PO number if found.
     * This checks the In-Reply-To header to find the parent email's PO number.
     *
     * @param email the incoming email message
     * @return the PO number if the parent email exists, otherwise null
     */
    @Transactional(readOnly = true)
    public String getExistingPoNumberIfExists(EmailMessage email) {
        if (email == null || email.getInReplyTo() == null || email.getInReplyTo().isEmpty()) {
            log.debug("No In-Reply-To header found, this is a new thread");
            return null;
        }

        log.debug("Looking for parent email with messageId: {}", email.getInReplyTo());
        
        Optional<EmailThread> parentThread = emailThreadRepository.findByMessageId(email.getInReplyTo());
        
        if (parentThread.isPresent()) {
            String poNumber = parentThread.get().getPoNumber();
            log.info("Found parent thread with PO number: {}", poNumber);
            return poNumber;
        }
        
        log.debug("No parent thread found for In-Reply-To: {}", email.getInReplyTo());
        return null;
    }


    /**
     * DTO for hierarchical email thread structure.
     */
    public static class EmailThreadNode {
        private EmailThread email;
        private List<EmailThreadNode> children;
        
        public EmailThreadNode(EmailThread email) {
            this.email = email;
            this.children = new ArrayList<>();
        }
        
        public EmailThread getEmail() {
            return email;
        }
        
        public void setEmail(EmailThread email) {
            this.email = email;
        }
        
        public List<EmailThreadNode> getChildren() {
            return children;
        }
        
        public void setChildren(List<EmailThreadNode> children) {
            this.children = children;
        }
    }
}
