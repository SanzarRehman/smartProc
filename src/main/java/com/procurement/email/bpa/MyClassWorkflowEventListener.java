package com.procurement.email.bpa;


import com.bracits.abs.bpaclient.dto.*;
import com.procurement.email.service.EmailProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Async
@Component
@Log4j2
@RequiredArgsConstructor
public class MyClassWorkflowEventListener implements
    ApplicationListener<MyClassWorkflowActionEvent> {

  private final EmailProcessingService emailProcessingService;

  @Override
  public void onApplicationEvent(MyClassWorkflowActionEvent event) {
    if (event.type.equals(EventType.After)) {
      TaskPerformResponse taskPerformResponse = (TaskPerformResponse) event.getSource();
      Action action = taskPerformResponse.getAction();

      if (Objects.isNull(action)) {
        return;
      }

      // refId is the PO number (might have additional suffix from BPA system)
      String rawPoNumber = event.dto.getRef();
      
      log.info("========================================");
      log.info("Workflow action received: {}, Raw PO from event.dto.getRef(): '{}'", action.getName(), rawPoNumber);
      log.info("Event DTO details - Key: {}, Ref: {}, Action: {}", event.dto.getKey(), event.dto.getRef(), event.dto.getAction());
      log.info("Raw PO Number length: {}", rawPoNumber != null ? rawPoNumber.length() : "null");
      
      // Extract actual PO number (format: PO-YYYY-NNNN)
      // The BPA system might append additional identifiers like -XXX
      String poNumber = extractPoNumber(rawPoNumber);
      log.info("Extracted PO Number: '{}'", poNumber);
      log.info("========================================");

//      switch (action.getName().toLowerCase()) {
//        case "approved_for_inventory":
//          log.info("Processing approved inventory allocation for PO: {}", poNumber);
//          try {
//            emailProcessingService.resumeInventoryAllocation(poNumber);
//            log.info("Successfully processed approved inventory allocation for PO: {}", poNumber);
//          } catch (Exception e) {
//            log.error("Failed to process approved inventory allocation for PO: {}", poNumber, e);
//          }
//          break;
//
//        case "approved_for_procurement":
//          log.info("Processing approved procurement for PO: {}", poNumber);
//          try {
//            emailProcessingService.resumeProcurement(poNumber);
//            log.info("Successfully processed approved procurement for PO: {}", poNumber);
//          } catch (Exception e) {
//            log.error("Failed to process approved procurement for PO: {}", poNumber, e);
//          }
//          break;
//
//        case "rejected":
//          log.info("Procurement rejected for PO: {}", poNumber);
//          // TODO: Handle rejection - send rejection email to requester
//          break;
//
//        case "approved":
//          log.info("Generic approval for PO: {}", poNumber);
//          // TODO: Handle generic approval if needed
//          break;
//
//        default:
//          log.warn("Unknown action: {} for PO: {}", action.getName(), poNumber);
//      }

    }
    else if (event.type.equals(EventType.Before)) {

      WorkflowDto taskPerformRequest = (WorkflowDto) event.dto;
      String action = taskPerformRequest.getAction();
      String poNumber = event.dto.getRef();
      switch (action.toLowerCase()) {
        case "approved_for_inventory":
          log.info("Processing approved inventory allocation for PO: {}", poNumber);
          try {
            emailProcessingService.resumeInventoryAllocation(poNumber);
            log.info("Successfully processed approved inventory allocation for PO: {}", poNumber);
          } catch (Exception e) {
            log.error("Failed to process approved inventory allocation for PO: {}", poNumber, e);
          }
          break;

        case "approved_for_procurement":
          log.info("Processing approved procurement for PO: {}", poNumber);
          try {
            emailProcessingService.resumeProcurement(poNumber);
            log.info("Successfully processed approved procurement for PO: {}", poNumber);
          } catch (Exception e) {
            log.error("Failed to process approved procurement for PO: {}", poNumber, e);
          }
          break;

        case "rejected":
          log.info("Procurement rejected for PO: {}", poNumber);
          // TODO: Handle rejection - send rejection email to requester
          break;

        case "approved":
          log.info("Generic approval for PO: {}", poNumber);
          // TODO: Handle generic approval if needed
          break;

        default:
          log.warn("Unknown action: {} for PO: {}", action, poNumber);
      }

      //do something
      /**
       * Event type before is used to perform validation before the action is performed.
       */

    } else if (event.type.equals(EventType.Abort)) {
      /**
       * Event type abort is used to perform action when the workflow got exception.
       */

    }
  }
  
  /**
   * Extracts the actual PO number from the BPA reference ID.
   * BPA system might append additional identifiers (e.g., PO-2025-0058-033)
   * We need to extract just PO-YYYY-NNNN format (first 3 segments).
   * 
   * @param rawPoNumber the raw reference ID from BPA
   * @return the extracted PO number in format PO-YYYY-NNNN
   */
  private String extractPoNumber(String rawPoNumber) {
    if (rawPoNumber == null || rawPoNumber.isEmpty()) {
      return rawPoNumber;
    }
    
    // Split by hyphen
    String[] parts = rawPoNumber.split("-");
    
    // Expected format: PO-YYYY-NNNN (3 parts)
    // BPA might append: PO-YYYY-NNNN-XXX (4+ parts)
    if (parts.length >= 3) {
      // Return first 3 parts: PO-YYYY-NNNN
      return parts[0] + "-" + parts[1] + "-" + parts[2];
    }
    
    // If format is unexpected, return as-is
    log.warn("Unexpected PO number format: {}. Expected PO-YYYY-NNNN", rawPoNumber);
    return rawPoNumber;
  }
}