# Human Approval Workflow Implementation

## Overview

This document describes the human approval workflow integration for the Procurement Email Automation system. The system now pauses processing when user confirms their selection and sends the request for human approval before completing the transaction.

---

## Architecture

### Components

1. **ProcurementApprovalBuffer** - Entity to store procurement context during approval
2. **ProcurementApprovalBufferRepository** - JPA repository for buffer operations
3. **EmailProcessingService** - Main service with pause/resume logic
4. **MyClassWorkflowEventListener** - Listens for approval events and resumes processing
5. **HumanInTheMiddleService** - Sends approval requests to BPA system

---

## Workflow Flow

### Step 1: User Confirmation
```
User replies → System parses confirmation → Identifies inventory/procurement request
```

### Step 2: Save to Buffer & Send for Approval
```
EmailProcessingService.processConfirmationEmail()
  ↓
Saves ProcurementApprovalBuffer with:
  - PO Number (refId for BPA)
  - Requester email
  - Request data (serialized JSON)
  - Selected item ID (if inventory)
  - Approval type (inventory/procurement)
  - State: PENDING_APPROVAL
  ↓
Calls HumanInTheMiddleService.perform()
  ↓
Processing STOPS - waits for approval
```

### Step 3: Human Approves
```
Manager reviews in BPA system
  ↓
Approves/Rejects
  ↓
BPA triggers workflow action
```

### Step 4: Resume Processing
```
MyClassWorkflowEventListener receives event
  ↓
Extracts PO number from refId
  ↓
Calls appropriate resume method:
  - approved_for_inventory → resumeInventoryAllocation()
  - approved_for_procurement → resumeProcurement()
```

### Step 5: Complete Transaction
```
Resume method:
  1. Loads buffer by PO number
  2. Deserializes request data
  3. Retrieves user context
  4. Processes transaction (inventory/procurement)
  5. Generates purchase order
  6. Records in accounting
  7. Sends confirmation email to user
  8. Updates buffer state to APPROVED
```

---

## Database Schema

### procurement_approval_buffer Table

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| po_number | VARCHAR | PO number (unique, used as refId) |
| requester_email | VARCHAR | Email of requester |
| original_message_id | VARCHAR | Original email message ID |
| original_subject | VARCHAR | Original email subject |
| approval_type | VARCHAR | 'inventory' or 'procurement' |
| username | VARCHAR | Username from Keycloak |
| request_data | TEXT | Serialized ProcurementRequest JSON |
| selected_item_id | VARCHAR | Item ID (for inventory allocations) |
| quantity | INTEGER | Requested quantity |
| current_state | VARCHAR | PENDING_APPROVAL, APPROVED, REJECTED |
| created_at | TIMESTAMP | When request was created |
| approved_at | TIMESTAMP | When approved/rejected |
| approved_by | VARCHAR | Who approved (future use) |
| notes | TEXT | Additional notes (future use) |

---

## Code Examples

### 1. Saving to Buffer (EmailProcessingService)

```java
if (confirmation.isFromInventory()) {
    // Save context
    saveToApprovalBuffer(poNumber, email, request, userContext, "inventory", selectedItem.getId());
    
    // Send for approval
    humanInTheMiddleService.perform(email.getFrom(), poNumber, email.getMessageId(),
        email.getSubject(), userContext.getUsername(), false, "inventory");
    
    updateState(state, "PENDING_INVENTORY_APPROVAL", request);
    return; // STOP HERE - wait for approval
}
```

### 2. Resuming After Approval (MyClassWorkflowEventListener)

```java
case "approved_for_inventory":
    String poNumber = event.dto.getRef(); // Get PO number from refId
    emailProcessingService.resumeInventoryAllocation(poNumber);
    break;

case "approved_for_procurement":
    String poNumber = event.dto.getRef();
    emailProcessingService.resumeProcurement(poNumber);
    break;
```

### 3. Resume Inventory Allocation

```java
public void resumeInventoryAllocation(String poNumber) {
    // Load buffer
    ProcurementApprovalBuffer buffer = approvalBufferRepository.findByPoNumber(poNumber)
        .orElseThrow();
    
    // Deserialize
    ProcurementRequest request = objectMapper.readValue(buffer.getRequestData(), ProcurementRequest.class);
    
    // Get context
    UserContext userContext = getUserContext(buffer.getRequesterEmail()).orElseThrow();
    Item selectedItem = inventoryService.getItemById(buffer.getSelectedItemId());
    
    // Process
    inventoryService.decrementItemQuantity(selectedItem.getId(), request.getQuantity());
    accountingAgentService.recordInventoryAllocation(selectedItem, userContext);
    PurchaseOrder po = procurementAgentService.generatePurchaseOrder(request, userContext, selectedItem);
    
    // Notify user
    emailSenderService.sendConfirmationEmail(buffer.getRequesterEmail(), po, 
                                            buffer.getOriginalMessageId(), buffer.getOriginalSubject());
    
    // Update buffer
    buffer.setCurrentState("APPROVED");
    approvalBufferRepository.save(buffer);
}
```

---

## Processing States

### Before Approval Flow
```
EMAIL_RECEIVED → CLASSIFIED → CONTEXT_GATHERED → INVENTORY_CHECKED → 
RECOMMENDATIONS_SENT → CONFIRMATION_RECEIVED → PENDING_INVENTORY_APPROVAL (STOP)
```

### After Approval
```
PENDING_INVENTORY_APPROVAL → (HUMAN APPROVES) → 
PO_GENERATED → ACCOUNTING_UPDATED → COMPLETION_SENT
```

---

## API Integration

### HumanInTheMiddleService.perform() Parameters

```java
perform(
    String requesterEmail,    // User's email
    String poNumber,          // PO number (becomes refId in BPA)
    String messageId,         // Email message ID
    String subject,           // Email subject
    String username,          // Username from Keycloak
    boolean isUrgent,         // Urgency flag
    String approvalType       // "inventory" or "proc"
)
```

### BPA Workflow Actions

| Action Name | Description | Handler Method |
|-------------|-------------|----------------|
| `approved_for_inventory` | Manager approved inventory allocation | `resumeInventoryAllocation()` |
| `approved_for_procurement` | Manager approved new purchase | `resumeProcurement()` |
| `rejected` | Manager rejected request | TODO: Handle rejection |

---

## Email Thread Tracking

All system emails (including confirmation emails) are saved to the email thread:

```java
emailSenderService.sendConfirmationEmail(
    requesterEmail,
    purchaseOrder,
    originalMessageId,      // Parent message ID for threading
    originalSubject
);
```

This ensures:
- ✅ Complete conversation history in database
- ✅ Proper email threading (In-Reply-To headers)
- ✅ UI can display full thread hierarchy

---

## Testing the Workflow

### 1. User Sends Request
```bash
# Send email: "I need a laptop"
# System responds with recommendations
```

### 2. User Confirms
```bash
# User replies: "I'll take option 1"
# System saves to buffer and sends for approval
```

### 3. Check Buffer
```sql
SELECT * FROM procurement_approval_buffer WHERE current_state = 'PENDING_APPROVAL';
```

### 4. Simulate Approval
```java
// In BPA system, manager approves
// Event triggers with refId = PO number
// System resumes and completes processing
```

### 5. Verify Completion
```sql
SELECT * FROM procurement_approval_buffer WHERE po_number = 'PO-2025-0001';
-- Should show current_state = 'APPROVED'

SELECT * FROM email_threads WHERE po_number = 'PO-2025-0001';
-- Should show confirmation email as final message
```

---

## Future Enhancements

1. **Rejection Handling**
   - Send rejection email to user
   - Update buffer state to REJECTED
   - Allow user to modify request

2. **Approval Timeout**
   - Auto-escalate after X hours
   - Send reminder emails to manager

3. **Multi-level Approval**
   - Different approval chains based on amount
   - Sequential approvals (manager → director → CFO)

4. **Approval Dashboard**
   - REST API to fetch pending approvals
   - Statistics and metrics

---

## Configuration

### Application Properties
```yaml
# BPA Integration
bpa:
  enabled: true
  workflow-id: procurement-approval
  
# Email Thread
email:
  thread:
    save-system-emails: true
```

---

## Troubleshooting

### Issue: Approval not resuming
**Check:**
1. Buffer exists: `SELECT * FROM procurement_approval_buffer WHERE po_number = 'PO-XXX'`
2. Event listener registered: Check logs for "Workflow action received"
3. PO number matches: refId in BPA must equal po_number in buffer

### Issue: Email thread broken
**Check:**
1. Parent message ID saved correctly
2. System emails being saved: Look for "Saved system email to thread" in logs

### Issue: Context lost after approval
**Check:**
1. request_data in buffer is valid JSON
2. ObjectMapper can deserialize: Check for JsonProcessingException

---

**Last Updated**: November 9, 2025  
**Version**: 1.0.0
