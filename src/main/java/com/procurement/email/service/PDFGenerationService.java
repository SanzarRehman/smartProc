package com.procurement.email.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.procurement.email.model.RequestForRequisition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Service for generating PDF documents from RRF data.
 * Converts HTML content to professional PDF documents.
 */
@Service
@Slf4j
public class PDFGenerationService {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
    
    /**
     * Generates a PDF document for an RRF.
     * If RRF content is HTML, converts it directly to PDF.
     * Otherwise, creates a formatted PDF from the data.
     */
    public byte[] generateRRFPDF(RequestForRequisition rrf) {
        log.info("Generating PDF for RRF: {}", rrf.getPoNumber());
        
        try {
            // Check if the content is HTML
            String rrfContent = rrf.getRrfContent();
            if (rrfContent != null && !rrfContent.trim().isEmpty()) {
                String trimmed = rrfContent.trim();
                
                // Check if content looks like HTML
                if (trimmed.startsWith("<") || trimmed.toLowerCase().contains("<html") || 
                    trimmed.toLowerCase().contains("<!doctype")) {
                    log.info("Detected HTML content, converting directly to PDF");
                    // Content is HTML, convert directly to PDF
                    return convertHtmlToPdf(rrfContent, rrf);
                } else {
                    log.info("Content is plain text, creating structured PDF");
                    // Content is plain text, create structured PDF
                    return createStructuredPdf(rrf);
                }
            } else {
                log.warn("No RRF content found, creating basic structured PDF");
                return createStructuredPdf(rrf);
            }
            
        } catch (Exception e) {
            log.error("Failed to generate PDF for RRF: {}", rrf.getPoNumber(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
    
    /**
     * Converts HTML content directly to PDF using iText's html2pdf.
     */
    private byte[] convertHtmlToPdf(String htmlContent, RequestForRequisition rrf) {
        log.info("Converting HTML to PDF for RRF: {}", rrf.getPoNumber());
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Clean up HTML if it has markdown code blocks
            String cleanHtml = cleanHtmlContent(htmlContent);
            
            log.debug("HTML content length: {} characters", cleanHtml.length());
            log.debug("HTML starts with: {}", cleanHtml.substring(0, Math.min(100, cleanHtml.length())));
            
            // Convert HTML to PDF directly - no wrapping, no modifications
            HtmlConverter.convertToPdf(cleanHtml, baos);
            
            log.info("PDF generated successfully from HTML for RRF: {} ({} bytes)", 
                     rrf.getPoNumber(), baos.size());
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to convert HTML to PDF for RRF: {}, error: {}", 
                     rrf.getPoNumber(), e.getMessage(), e);
            throw new RuntimeException("HTML to PDF conversion failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cleans HTML content by removing markdown code blocks and ensuring proper HTML structure.
     */
    private String cleanHtmlContent(String html) {
        if (html == null || html.trim().isEmpty()) {
            return html;
        }
        
        // Remove markdown code blocks if present
        String cleaned = html.replaceAll("```html\\s*", "")
                            .replaceAll("```\\s*$", "")
                            .trim();
        
        // Check if it already has proper HTML structure
        String lowerCleaned = cleaned.toLowerCase();
        boolean hasDoctype = lowerCleaned.startsWith("<!doctype");
        boolean hasHtmlTag = lowerCleaned.contains("<html");
        
        // Only wrap if it doesn't have proper HTML structure
        if (!hasDoctype && !hasHtmlTag) {
            log.info("HTML content missing structure, adding basic wrapper");
            cleaned = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n</head>\n<body>\n" + 
                     cleaned + "\n</body>\n</html>";
        } else {
            log.info("HTML content has proper structure, using as-is");
        }
        
        return cleaned;
    }
    
    /**
     * Creates a structured PDF from RRF data (fallback method).
     */
    private byte[] createStructuredPdf(RequestForRequisition rrf) {
        log.info("Creating structured PDF for RRF: {}", rrf.getPoNumber());
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 40, 40, 40);
            
            // Add header
            Paragraph header = new Paragraph("REQUEST FOR REQUISITION")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceRgb(41, 128, 185))
                    .setMarginBottom(10);
            document.add(header);
            
            // Add RRF Number
            Paragraph rrfNum = new Paragraph("RRF Number: " + rrf.getPoNumber())
                    .setFontSize(12)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5);
            document.add(rrfNum);
            
            // Add Date
            Paragraph date = new Paragraph("Date: " + 
                    (rrf.getCreatedAt() != null ? rrf.getCreatedAt().format(DATE_FORMATTER) : "N/A"))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(date);
            
            // Add sections
            addSection(document, "Requester Information");
            addField(document, "Name", rrf.getRequesterName());
            addField(document, "Email", rrf.getRequesterEmail());
            
            addSection(document, "Item Details");
            addField(document, "Item Name", rrf.getItemName());
            addField(document, "Category", rrf.getItemType());
            addField(document, "Quantity", rrf.getQuantity() != null ? rrf.getQuantity() + " unit(s)" : "N/A");
            addField(document, "Estimated Amount", rrf.getEstimatedAmount() != null ? "$" + rrf.getEstimatedAmount() : "N/A");
            
            if (rrf.getSpecifications() != null && !rrf.getSpecifications().trim().isEmpty()) {
                addSection(document, "Specifications");
                document.add(new Paragraph(rrf.getSpecifications())
                        .setFontSize(10)
                        .setMarginLeft(15)
                        .setMarginBottom(10));
            }
            
            if (rrf.getRrfContent() != null && !rrf.getRrfContent().trim().isEmpty()) {
                addSection(document, "Detailed Requisition");
                document.add(new Paragraph(rrf.getRrfContent())
                        .setFontSize(10)
                        .setMarginBottom(20));
            }
            
            // Add footer
            document.add(new Paragraph("\n\n_________________    _________________    _________________")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30));
            document.add(new Paragraph("Requested By              Department Head              Finance Approval")
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER));
            
            document.close();
            
            log.info("Structured PDF generated successfully for RRF: {}", rrf.getPoNumber());
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to create structured PDF for RRF: {}", rrf.getPoNumber(), e);
            throw new RuntimeException("Failed to generate structured PDF", e);
        }
    }
    
    private void addSection(Document document, String title) {
        document.add(new Paragraph(title)
                .setFontSize(14)
                .setBold()
                .setFontColor(new DeviceRgb(41, 128, 185))
                .setMarginTop(15)
                .setMarginBottom(5));
    }
    
    private void addField(Document document, String label, String value) {
        document.add(new Paragraph(label + ": " + (value != null ? value : "N/A"))
                .setFontSize(10)
                .setMarginLeft(10)
                .setMarginBottom(3));
    }
}
