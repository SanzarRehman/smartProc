package com.procurement.email.service;

import com.procurement.email.model.Item;
import com.procurement.email.model.PurchaseOrder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service for sending procurement-related emails with retry logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderService {

    private final JavaMailSender mailSender;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Sends a recommendation email with a list of suggested items from inventory.
     *
     * @param to the recipient email address
     * @param recommendations the list of recommended items
     * @param request the original procurement request
     * @param inReplyToMessageId the message ID of the original email (for threading)
     * @param originalSubject the subject of the original email
     */
    public void sendRecommendationEmail(String to, List<Item> recommendations, 
                                       com.procurement.email.model.ProcurementRequest request,
                                       String inReplyToMessageId, String originalSubject) {
        log.info("Sending recommendation email to: {} (in reply to: {})", to, inReplyToMessageId);
        
        // Use "Re: " + original subject for proper email threading
        String subject = originalSubject;
        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }
        
        String body = buildRecommendationEmailBody(recommendations, request);
        
        sendEmailWithRetry(to, subject, body, inReplyToMessageId);
    }
    
    /**
     * Sends a confirmation request email for new purchase (no inventory match).
     *
     * @param to the recipient email address
     * @param request the procurement request
     * @param inReplyToMessageId the message ID of the original email (for threading)
     * @param originalSubject the subject of the original email
     */
    public void sendNewPurchaseConfirmationEmail(String to, 
                                                com.procurement.email.model.ProcurementRequest request,
                                                String inReplyToMessageId, String originalSubject) {
        log.info("Sending new purchase confirmation request to: {} (in reply to: {})", to, inReplyToMessageId);
        
        // Use "Re: " + original subject for proper email threading
        String subject = originalSubject;
        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }
        
        String body = buildNewPurchaseConfirmationEmailBody(request);
        
        sendEmailWithRetry(to, subject, body, inReplyToMessageId);
    }

    /**
     * Sends a confirmation email with purchase order details.
     *
     * @param to the recipient email address
     * @param po the purchase order
     * @param inReplyToMessageId the message ID of the original email (for threading)
     * @param originalSubject the subject of the original email
     */
    public void sendConfirmationEmail(String to, PurchaseOrder po, String inReplyToMessageId, String originalSubject) {
        log.info("Sending confirmation email to: {} for PO: {} (in reply to: {})", to, po.getPoNumber(), inReplyToMessageId);
        
        // Use "Re: " + original subject for proper email threading
        String subject = originalSubject;
        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }
        
        String body = buildConfirmationEmailBody(po);
        
        sendEmailWithRetry(to, subject, body, inReplyToMessageId);
    }

    /**
     * Sends an error notification email.
     *
     * @param to the recipient email address
     * @param errorMessage the error message to include
     */
    public void sendErrorEmail(String to, String errorMessage) {
        log.info("Sending error email to: {}", to);
        
        String subject = "Procurement Request - Processing Error";
        String body = buildErrorEmailBody(errorMessage);
        
        sendEmailWithRetry(to, subject, body);
    }

    /**
     * Sends a template guide email when the procurement request doesn't match the required format.
     *
     * @param to the recipient email address
     * @param missingFields list of missing required fields
     * @param inReplyToMessageId the message ID of the original email (for threading)
     * @param originalSubject the subject of the original email
     */
    public void sendTemplateGuideEmail(String to, List<String> missingFields, String inReplyToMessageId, String originalSubject) {
        log.info("Sending template guide email to: {} (missing fields: {})", to, missingFields);
        
        // Use "Re: " + original subject for proper email threading
        String subject = originalSubject;
        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }
        
        String body = buildTemplateGuideEmailBody(missingFields);
        
        sendEmailWithRetry(to, subject, body, inReplyToMessageId);
    }

    /**
     * Sends a user registration request email.
     *
     * @param to the recipient email address
     */
    public void sendRegistrationRequestEmail(String to) {
        log.info("Sending registration request email to: {}", to);
        
        String subject = "Procurement System - Registration Required";
        String body = buildRegistrationRequestEmailBody();
        
        sendEmailWithRetry(to, subject, body);
    }

    /**
     * Sends an email with retry logic (up to 3 attempts).
     *
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body (HTML)
     */
    private void sendEmailWithRetry(String to, String subject, String body) {
        sendEmailWithRetry(to, subject, body, null);
    }
    
    private void sendEmailWithRetry(String to, String subject, String body, String inReplyToMessageId) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++;
            try {
                sendEmail(to, subject, body, inReplyToMessageId);
                log.info("Email sent successfully to {} on attempt {}", to, attempt);
                return;
            } catch (Exception e) {
                lastException = e;
                log.warn("Failed to send email to {} on attempt {}: {}", to, attempt, e.getMessage());
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry interrupted for email to {}", to);
                        break;
                    }
                }
            }
        }
        
        log.error("Failed to send email to {} after {} attempts", to, MAX_RETRY_ATTEMPTS, lastException);
        throw new EmailDeliveryException("Failed to send email after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
    }

    /**
     * Sends an email using JavaMailSender.
     *
     * @param to the recipient email address
     * @param subject the email subject
     * @param body the email body (HTML)
     * @throws MessagingException if email sending fails
     */
    private void sendEmail(String to, String subject, String body) throws MessagingException {
        sendEmail(to, subject, body, null);
    }
    
    private void sendEmail(String to, String subject, String body, String inReplyTo) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom("procurementpoc2025@gmail.com");  // Set from address
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true); // true indicates HTML
        
        // If this is a reply, set the In-Reply-To and References headers for email threading
        if (inReplyTo != null && !inReplyTo.isEmpty()) {
            log.info("Setting reply headers - In-Reply-To: {}", inReplyTo);
            message.setHeader("In-Reply-To", inReplyTo);
            message.setHeader("References", inReplyTo);
            
            // Also set the Message-ID to help with threading
            // Gmail uses these headers to thread conversations
        } else {
            log.debug("Sending new email (not a reply)");
        }
        
        mailSender.send(message);
        log.info("Email sent to: {} with subject: {}", to, subject);
    }

    /**
     * Builds the HTML body for template guide email.
     *
     * @param missingFields list of missing required fields
     * @return the HTML email body
     */
    private String buildTemplateGuideEmailBody(List<String> missingFields) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>Procurement Request - Additional Information Needed</h2>");
        html.append("<p>Dear Requester,</p>");
        html.append("<p>Thank you for your procurement request. To process it efficiently, we need a bit more information:</p>");
        
        if (!missingFields.isEmpty()) {
            html.append("<div style='background-color: #fff3cd; padding: 15px; border-left: 4px solid #ffc107; margin: 15px 0;'>");
            html.append("<p><strong>Please provide:</strong></p>");
            html.append("<ul style='margin: 5px 0;'>");
            for (String field : missingFields) {
                html.append("<li><strong>").append(escapeHtml(field)).append("</strong>");
                if (field.contains("Item Type")) {
                    html.append(" - What item/equipment do you need? (e.g., laptop, monitor, mouse, software, furniture, office supplies)");
                } else if (field.contains("Quantity")) {
                    html.append(" - How many units do you need? (e.g., 1, 2, 5)");
                }
                html.append("</li>");
            }
            html.append("</ul>");
            html.append("</div>");
        }
        
        html.append("<hr>");
        html.append("<h3>Procurement Request Guide</h3>");
        html.append("<p>You can structure your request in any natural way, or use this template:</p>");
        html.append("<div style='background-color: #f5f5f5; padding: 15px; border: 1px solid #ddd; margin: 15px 0;'>");
        html.append("<p style='margin: 5px 0;'><strong>Item Type:</strong> [What you need - laptop, monitor, software, furniture, supplies, etc.]</p>");
        html.append("<p style='margin: 5px 0;'><strong>Quantity:</strong> [How many units]</p>");
        html.append("<p style='margin: 5px 0;'><strong>Specifications:</strong> [Optional - any specific requirements, features, or models]</p>");
        html.append("<p style='margin: 5px 0;'><strong>Reason:</strong> [Optional - why you need this]</p>");
        html.append("<p style='margin: 5px 0;'><strong>Notes:</strong> [Optional - urgency, preferences, etc.]</p>");
        html.append("</div>");
        
        html.append("<h4>Examples:</h4>");
        html.append("<div style='background-color: #e8f5e9; padding: 10px; border-left: 3px solid #4caf50; margin: 10px 0;'>");
        html.append("<p style='margin: 5px 0; font-style: italic;'>\"I need 2 laptops with 16GB RAM for development work. Urgent.\"</p>");
        html.append("</div>");
        html.append("<div style='background-color: #e8f5e9; padding: 10px; border-left: 3px solid #4caf50; margin: 10px 0;'>");
        html.append("<p style='margin: 5px 0; font-style: italic;'>\"Item Type: Wireless Mouse<br>Quantity: 5<br>For the new team members\"</p>");
        html.append("</div>");
        html.append("<div style='background-color: #e8f5e9; padding: 10px; border-left: 3px solid #4caf50; margin: 10px 0;'>");
        html.append("<p style='margin: 5px 0; font-style: italic;'>\"Need a 27-inch monitor, preferably 4K resolution\"</p>");
        html.append("</div>");
        
        html.append("<p><strong>Simply reply to this email with the information above.</strong></p>");
        html.append("<p>We'll process your request as soon as we receive the complete details.</p>");
        
        html.append("<p>Best regards,<br>Procurement Automation System</p>");
        html.append("</body></html>");
        
        return html.toString();
    }

    /**
     * Builds the HTML body for recommendation email with inventory items.
     *
     * @param recommendations the list of recommended items
     * @param request the original procurement request
     * @return the HTML email body
     */
    private String buildRecommendationEmailBody(List<Item> recommendations, 
                                               com.procurement.email.model.ProcurementRequest request) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>✓ Available Items Found</h2>");
        html.append("<p>Dear Requester,</p>");
        
        // Show what they requested
        html.append("<div style='background-color: #e3f2fd; padding: 15px; border-left: 4px solid #2196f3; margin: 15px 0;'>");
        html.append("<p style='margin: 5px 0;'><strong>Your Request:</strong></p>");
        html.append("<p style='margin: 5px 0;'>Item: <strong>").append(escapeHtml(request.getItemName() != null ? request.getItemName() : request.getItemType())).append("</strong></p>");
        html.append("<p style='margin: 5px 0;'>Quantity: <strong>").append(request.getQuantity()).append("</strong></p>");
        if (request.getAdditionalNotes() != null && !request.getAdditionalNotes().isEmpty()) {
            html.append("<p style='margin: 5px 0;'>Notes: ").append(escapeHtml(request.getAdditionalNotes())).append("</p>");
        }
        html.append("</div>");
        
        html.append("<p>Great news! We found matching items in our inventory:</p>");
        
        if (recommendations.isEmpty()) {
            html.append("<p><strong>No matching items found in inventory.</strong></p>");
            html.append("<p>We'll proceed with a new purchase order.</p>");
        } else {
            html.append("<div style='margin: 20px 0;'>");
            
            for (int i = 0; i < recommendations.size(); i++) {
                Item item = recommendations.get(i);
                html.append("<div style='border: 2px solid #4caf50; border-radius: 8px; padding: 15px; margin: 15px 0; background-color: #f9f9f9;'>");
                html.append("<h3 style='margin-top: 0; color: #2e7d32;'>Option ").append(i + 1).append(": ").append(escapeHtml(item.getName())).append("</h3>");
                html.append("<p style='font-size: 18px; color: #1976d2; margin: 10px 0;'><strong>Price: $").append(formatCurrency(item.getBookValue())).append(" per unit</strong></p>");
                html.append("<p style='margin: 5px 0;'><strong>Item ID:</strong> ").append(escapeHtml(item.getId())).append("</p>");
                html.append("<p style='margin: 5px 0;'><strong>Available Quantity:</strong> ").append(item.getAvailableQuantity()).append(" units</p>");
                
                if (item.getSpecifications() != null && !item.getSpecifications().isEmpty()) {
                    html.append("<p style='margin: 10px 0 5px 0;'><strong>Specifications:</strong></p>");
                    html.append("<ul style='margin: 5px 0;'>");
                    for (Map.Entry<String, String> entry : item.getSpecifications().entrySet()) {
                        if (!entry.getKey().equals("subtype")) {
                            html.append("<li><strong>").append(escapeHtml(formatSpecKey(entry.getKey()))).append(":</strong> ")
                                .append(escapeHtml(entry.getValue())).append("</li>");
                        }
                    }
                    html.append("</ul>");
                }
                
                // Calculate total for requested quantity
                java.math.BigDecimal total = item.getBookValue().multiply(java.math.BigDecimal.valueOf(request.getQuantity()));
                html.append("<p style='margin: 10px 0; padding: 10px; background-color: #fff3cd; border-radius: 4px;'>");
                html.append("<strong>Total for ").append(request.getQuantity()).append(" unit(s): $").append(formatCurrency(total)).append("</strong>");
                html.append("</p>");
                
                html.append("</div>");
            }
            
            html.append("</div>");
            
            html.append("<div style='background-color: #fff3cd; padding: 15px; border-left: 4px solid #ffc107; margin: 20px 0;'>");
            html.append("<p style='margin: 0;'><strong>To confirm your selection:</strong></p>");
            html.append("<p style='margin: 10px 0;'>Simply <strong>reply to this email</strong> with:</p>");
            html.append("<ul style='margin: 5px 0;'>");
            html.append("<li>The <strong>Item ID</strong> (e.g., \"").append(escapeHtml(recommendations.get(0).getId())).append("\"), OR</li>");
            html.append("<li>The <strong>Item Name</strong> (e.g., \"").append(escapeHtml(recommendations.get(0).getName())).append("\"), OR</li>");
            html.append("<li>Just say \"<strong>Option 1</strong>\" or \"<strong>Confirm first option</strong>\"</li>");
            html.append("</ul>");
            html.append("</div>");
        }
        
        html.append("<p>Best regards,<br>Procurement Automation System</p>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * Builds the HTML body for new purchase confirmation email (no inventory match).
     *
     * @param request the procurement request
     * @return the HTML email body
     */
    private String buildNewPurchaseConfirmationEmailBody(com.procurement.email.model.ProcurementRequest request) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>Procurement Request - New Purchase Required</h2>");
        html.append("<p>Dear Requester,</p>");
        
        html.append("<div style='background-color: #e3f2fd; padding: 15px; border-left: 4px solid #2196f3; margin: 15px 0;'>");
        html.append("<p style='margin: 5px 0;'><strong>Your Request:</strong></p>");
        html.append("<p style='margin: 5px 0;'>Item: <strong>").append(escapeHtml(request.getItemName() != null ? request.getItemName() : request.getItemType())).append("</strong></p>");
        html.append("<p style='margin: 5px 0;'>Quantity: <strong>").append(request.getQuantity()).append("</strong></p>");
        if (request.getSpecifications() != null && !request.getSpecifications().isEmpty()) {
            html.append("<p style='margin: 10px 0 5px 0;'><strong>Specifications:</strong></p>");
            html.append("<ul style='margin: 5px 0;'>");
            for (Map.Entry<String, String> entry : request.getSpecifications().entrySet()) {
                html.append("<li><strong>").append(escapeHtml(formatSpecKey(entry.getKey()))).append(":</strong> ")
                    .append(escapeHtml(entry.getValue())).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</div>");
        
        html.append("<p>We checked our inventory and didn't find an exact match for your request.</p>");
        html.append("<p><strong>We will proceed with purchasing a new item from our vendors.</strong></p>");
        
        html.append("<div style='background-color: #fff3cd; padding: 15px; border-left: 4px solid #ffc107; margin: 20px 0;'>");
        html.append("<p style='margin: 0;'><strong>To confirm and proceed:</strong></p>");
        html.append("<p style='margin: 10px 0;'>Simply <strong>reply to this email</strong> with \"<strong>Confirm</strong>\" or \"<strong>Yes, proceed</strong>\"</p>");
        html.append("<p style='margin: 5px 0;'>We'll get quotes from vendors and process your order.</p>");
        html.append("</div>");
        
        html.append("<p>Best regards,<br>Procurement Automation System</p>");
        html.append("</body></html>");
        
        return html.toString();
    }

    /**
     * Builds the HTML body for confirmation email.
     *
     * @param po the purchase order
     * @return the HTML email body
     */
    private String buildConfirmationEmailBody(PurchaseOrder po) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>✓ Procurement Request Confirmed</h2>");
        html.append("<p>Dear ").append(escapeHtml(po.getRequesterEmail())).append(",</p>");
        html.append("<p>Great news! Your procurement request has been successfully processed and approved.</p>");
        html.append("<br>");
        
        html.append("<div style='background-color: #e8f5e9; padding: 15px; border-left: 4px solid #4caf50; margin: 15px 0;'>");
        html.append("<h3 style='margin-top: 0;'>Order Summary</h3>");
        html.append("<p style='font-size: 18px; margin: 5px 0;'><strong>").append(escapeHtml(po.getItemName())).append("</strong></p>");
        html.append("<p style='margin: 5px 0;'>Quantity: <strong>").append(po.getQuantity()).append("</strong></p>");
        html.append("<p style='margin: 5px 0;'>Total Amount: <strong>$").append(formatCurrency(po.getAmount())).append("</strong></p>");
        html.append("</div>");
        
        if (po.getSpecifications() != null && !po.getSpecifications().isEmpty()) {
            html.append("<h3>Item Specifications</h3>");
            html.append("<table border='0' cellpadding='5' cellspacing='0' style='margin-left: 20px;'>");
            for (Map.Entry<String, String> entry : po.getSpecifications().entrySet()) {
                // Skip the generic "type" field if it's redundant
                if (entry.getKey().equals("type") && entry.getValue().equals(po.getItemType())) {
                    continue;
                }
                html.append("<tr>");
                html.append("<td style='padding-right: 20px;'><strong>").append(escapeHtml(formatSpecKey(entry.getKey()))).append(":</strong></td>");
                html.append("<td>").append(escapeHtml(entry.getValue())).append("</td>");
                html.append("</tr>");
            }
            html.append("</table>");
            html.append("<br>");
        }
        
        html.append("<h3>Purchase Order Details</h3>");
        html.append("<table border='0' cellpadding='5' cellspacing='0' style='margin-left: 20px;'>");
        html.append("<tr><td style='padding-right: 20px;'><strong>PO Number:</strong></td><td>").append(escapeHtml(po.getPoNumber())).append("</td></tr>");
        html.append("<tr><td style='padding-right: 20px;'><strong>Status:</strong></td><td><span style='color: #ff9800;'>").append(escapeHtml(po.getStatus())).append("</span></td></tr>");
        html.append("<tr><td style='padding-right: 20px;'><strong>Order Date:</strong></td><td>").append(po.getCreatedAt().format(DATE_FORMATTER)).append("</td></tr>");
        html.append("<tr><td style='padding-right: 20px;'><strong>Estimated Delivery:</strong></td><td>5-7 business days</td></tr>");
        html.append("</table>");
        html.append("<br>");
        
        html.append("<div style='background-color: #fff3cd; padding: 15px; border-left: 4px solid #ffc107; margin: 15px 0;'>");
        html.append("<p style='margin: 0;'><strong>Next Steps:</strong></p>");
        html.append("<ul style='margin: 10px 0;'>");
        html.append("<li>Your order is being processed by the procurement team</li>");
        html.append("<li>You will receive updates via email as the order progresses</li>");
        html.append("<li>For urgent inquiries, reference PO Number: <strong>").append(escapeHtml(po.getPoNumber())).append("</strong></li>");
        html.append("</ul>");
        html.append("</div>");
        
        html.append("<p>Thank you for using the Procurement Automation System!</p>");
        html.append("<p>Best regards,<br>Procurement Team</p>");
        html.append("</body></html>");
        
        return html.toString();
    }
    
    /**
     * Formats specification keys to be more readable.
     * Converts "ram" to "RAM", "screen_size" to "Screen Size", etc.
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
     * Builds the HTML body for error email.
     *
     * @param errorMessage the error message
     * @return the HTML email body
     */
    private String buildErrorEmailBody(String errorMessage) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>Procurement Request - Processing Error</h2>");
        html.append("<p>Dear Requester,</p>");
        html.append("<p>We encountered an error while processing your procurement request.</p>");
        html.append("<br>");
        html.append("<div style='background-color: #ffebee; padding: 15px; border-left: 4px solid #f44336;'>");
        html.append("<strong>Error Details:</strong><br>");
        html.append(escapeHtml(errorMessage));
        html.append("</div>");
        html.append("<br>");
        html.append("<p>Please try submitting your request again or contact the procurement team for assistance.</p>");
        html.append("<p>Best regards,<br>Procurement Automation System</p>");
        html.append("</body></html>");
        
        return html.toString();
    }

    /**
     * Builds the HTML body for registration request email.
     *
     * @return the HTML email body
     */
    private String buildRegistrationRequestEmailBody() {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>Procurement System - Registration Required</h2>");
        html.append("<p>Dear User,</p>");
        html.append("<p>We received a procurement request from your email address, but we could not find your profile in our system.</p>");
        html.append("<br>");
        html.append("<p><strong>To process your procurement requests, please register in the system:</strong></p>");
        html.append("<ol>");
        html.append("<li>Contact your system administrator</li>");
        html.append("<li>Request access to the Procurement System</li>");
        html.append("<li>Complete your user profile with role and designation information</li>");
        html.append("</ol>");
        html.append("<br>");
        html.append("<p>Once registered, you will be able to submit procurement requests through email.</p>");
        html.append("<p>Best regards,<br>Procurement Automation System</p>");
        html.append("</body></html>");
        
        return html.toString();
    }

    /**
     * Formats specifications map as HTML list.
     *
     * @param specifications the specifications map
     * @return formatted HTML string
     */
    private String formatSpecifications(Map<String, String> specifications) {
        if (specifications == null || specifications.isEmpty()) {
            return "N/A";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("<ul style='margin: 0; padding-left: 20px;'>");
        for (Map.Entry<String, String> entry : specifications.entrySet()) {
            sb.append("<li>").append(escapeHtml(entry.getKey())).append(": ")
              .append(escapeHtml(entry.getValue())).append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    /**
     * Formats currency value.
     *
     * @param value the BigDecimal value
     * @return formatted currency string
     */
    private String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return String.format("%.2f", value);
    }

    /**
     * Escapes HTML special characters.
     *
     * @param text the text to escape
     * @return escaped text
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Custom exception for email delivery failures.
     */
    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
