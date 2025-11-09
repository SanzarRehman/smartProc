# Email Thread Management - Implementation Summary

## ✅ What Was Implemented

### 1. Database Schema
- **`email_threads` table**: Stores all emails with nested chain structure
  - PO number linking (every email gets tracked)
  - Parent-child relationships (`parent_message_id`)
  - Cleaned body (stripped HTML, signatures, quotes)
  - Raw content (original email preserved)
  - Processing state tracking
  - Procurement vs non-procurement flag

### 2. Core Features

#### PO Number Generation
- **Format**: `PO-YYYY-NNNN` (e.g., `PO-2025-0001`)
- **When**: Generated immediately when email arrives, before any processing
- **Scope**: Every email gets a unique PO number
- **Purpose**: Track all emails for audit trail, even non-procurement

#### Email Cleaning
- **HTML Parsing**: Converts HTML emails to clean plain text
- **Signature Removal**: Detects and removes common email signatures
- **Quote Removal**: Strips quoted reply text (lines starting with `>`)
- **Reply Header Removal**: Removes "On Mon, Nov 5, John wrote:" type headers
- **Whitespace Cleanup**: Normalizes spacing and line breaks
- **Preservation**: Original raw content always saved

#### Thread Hierarchy
- **Depth Levels**: 0 = root, 1 = first reply, 2 = reply to reply, etc.
- **Parent Tracking**: Links each email to its parent via `parent_message_id`
- **Nested Structure**: API can return flat list or hierarchical tree

### 3. API Endpoints Created

#### Email Thread APIs (`/api/emails`)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/po/{poNumber}` | GET | Get all emails in thread (flat list) |
| `/po/{poNumber}/hierarchy` | GET | Get nested email structure |
| `/message/{messageId}` | GET | Get specific email by message ID |
| `/sender/{email}` | GET | Get all emails from a sender |
| `/procurement` | GET | Get procurement emails only |
| `/state/{state}` | GET | Filter by processing state |
| `/recent?days=7` | GET | Get recent emails |
| `/po-numbers` | GET | List all PO numbers |
| `/po/{poNumber}/stats` | GET | Get thread statistics |
| `/search?query=mouse` | GET | Search by content |

### 4. Integration Points

#### EmailProcessingService
- ✅ Generates PO number at start of `processIncomingEmail()`
- ✅ Saves all emails (procurement or not) with PO number
- ✅ Updates processing state for tracking
- ✅ Links system-generated emails to thread

#### EmailCleaningService
- ✅ `saveEmailThread()` - Save incoming emails
- ✅ `saveSystemEmail()` - Save system-generated responses
- ✅ `cleanEmailBody()` - Clean email content
- ✅ `getEmailThreadHierarchy()` - Build nested structure

## 📊 Database Tables

### email_threads
```sql
CREATE TABLE email_threads (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    po_number VARCHAR(20) NOT NULL,
    message_id VARCHAR(500) NOT NULL UNIQUE,
    parent_message_id VARCHAR(500),
    from_email VARCHAR(255) NOT NULL,
    to_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    cleaned_body TEXT,
    raw_content TEXT,
    is_system_message BOOLEAN DEFAULT FALSE,
    is_procurement_related BOOLEAN DEFAULT TRUE,
    processing_state VARCHAR(50),
    received_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    depth_level INT DEFAULT 0,
    metadata TEXT,
    INDEX idx_po_number (po_number),
    INDEX idx_message_id (message_id),
    INDEX idx_parent_message_id (parent_message_id)
);
```

### email_processing_states (updated)
```sql
-- Now includes po_number field
ALTER TABLE email_processing_states 
ADD COLUMN po_number VARCHAR(20);
```

## 🎯 Use Cases Solved

### 1. Email Tracking
**Problem**: Can't track email conversations after processing  
**Solution**: Every email saved with PO number, can retrieve entire thread

### 2. Non-Procurement Emails
**Problem**: Non-procurement emails not saved, lost for audit  
**Solution**: All emails saved with state `NOT_PROCUREMENT`, still get PO number

### 3. Email Chain Display
**Problem**: Need to show conversation history in UI  
**Solution**: APIs provide both flat list and hierarchical structure

### 4. Content Cleaning
**Problem**: HTML emails, signatures, quotes clutter display  
**Solution**: Cleaned body stored separately, original preserved in raw_content

### 5. Search & Filter
**Problem**: Need to find specific emails or conversations  
**Solution**: Search by content, filter by state, sender, date range

## 🔧 Dependencies Added

```gradle
// JSoup for HTML parsing and email cleaning
implementation 'org.jsoup:jsoup:1.17.1'
```

## 📝 Example Usage

### Track an Email Flow

1. **Email arrives**: `john.doe@company.com` sends "Need a mouse"
2. **PO generated**: `PO-2025-0001`
3. **Email saved**: 
   ```json
   {
     "poNumber": "PO-2025-0001",
     "fromEmail": "john.doe@company.com",
     "cleanedBody": "Need a mouse",
     "isProcurementRelated": true,
     "processingState": "EMAIL_RECEIVED"
   }
   ```

4. **System responds**: Recommendations sent
   ```json
   {
     "poNumber": "PO-2025-0001",
     "fromEmail": "procurement@company.com",
     "parentMessageId": "<original-message-id>",
     "isSystemMessage": true,
     "processingState": "RECOMMENDATIONS_SENT"
   }
   ```

5. **User confirms**: "Yes, I want the Logitech"
   ```json
   {
     "poNumber": "PO-2025-0001",
     "fromEmail": "john.doe@company.com",
     "parentMessageId": "<system-message-id>",
     "cleanedBody": "Yes, I want the Logitech",
     "processingState": "CONFIRMATION_RECEIVED"
   }
   ```

### Retrieve in UI

```bash
# Get entire conversation
curl http://localhost:8080/api/emails/po/PO-2025-0001/hierarchy

# Get thread stats
curl http://localhost:8080/api/emails/po/PO-2025-0001/stats

# Search for specific content
curl "http://localhost:8080/api/emails/search?query=Logitech"
```

## 🚀 Next Steps for UI Development

### Display Email Thread
```javascript
// Fetch thread hierarchy
const response = await fetch(`/api/emails/po/${poNumber}/hierarchy`);
const thread = await response.json();

// Render nested structure
function renderThread(node, depth = 0) {
  return `
    <div style="margin-left: ${depth * 20}px">
      <strong>${node.email.fromEmail}</strong>
      <p>${node.email.cleanedBody}</p>
      ${node.children.map(child => renderThread(child, depth + 1)).join('')}
    </div>
  `;
}
```

### List All Conversations
```javascript
// Get all PO numbers
const poNumbers = await fetch('/api/emails/po-numbers').then(r => r.json());

// Get stats for each
const conversations = await Promise.all(
  poNumbers.map(po => 
    fetch(`/api/emails/po/${po}/stats`).then(r => r.json())
  )
);
```

### Search & Filter
```javascript
// Search emails
const results = await fetch(`/api/emails/search?query=${query}`)
  .then(r => r.json());

// Filter by state
const pending = await fetch('/api/emails/state/RECOMMENDATIONS_SENT')
  .then(r => r.json());
```

## ✅ Testing Checklist

- [ ] Test PO number generation (sequential, year-based)
- [ ] Test email saving (procurement and non-procurement)
- [ ] Test HTML cleaning (remove tags, signatures, quotes)
- [ ] Test thread hierarchy building (parent-child relationships)
- [ ] Test all API endpoints with sample data
- [ ] Test search functionality
- [ ] Test filtering by state/sender/date
- [ ] Test thread statistics calculation

## 📚 Documentation

- **API Examples**: See `API_EXAMPLES.md` for curl commands
- **Email Flow**: Documented in API_EXAMPLES.md
- **Code Structure**: 
  - `EmailThread.java` - Entity
  - `EmailThreadRepository.java` - Data access
  - `EmailCleaningService.java` - Business logic
  - `EmailThreadController.java` - REST endpoints
  - `EmailProcessingService.java` - Integration point
