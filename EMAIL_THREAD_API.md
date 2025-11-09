# Email Thread API Documentation

Complete API reference for managing email threads and conversations in the Procurement Email Automation system.

## Base URL
```
http://localhost:8080
```

---

## 📧 Overview

The Email Thread API provides endpoints to:
- Retrieve email conversations by PO number
- View nested email hierarchies (parent-child relationships)
- Search and filter emails
- Track email processing states
- Get conversation statistics

All emails (procurement and non-procurement) are tracked with a unique PO number for complete audit trail.

---

## 🔑 Key Concepts

### PO Number
- **Format**: `PO-YYYY-NNNN` (e.g., `PO-2025-0001`)
- Generated immediately when email arrives
- Links all emails in a conversation
- Used for tracking and auditing

### Email Thread
- Original email and all replies
- Parent-child relationships maintained
- Depth levels track conversation hierarchy
- Both user and system emails included

### Cleaned vs Raw Content
- **cleanedBody**: HTML stripped, signatures removed, clean text
- **rawContent**: Original email with all formatting preserved

---

## 📋 API Endpoints

### 1. Get Emails by PO Number

Retrieve all emails in a conversation thread (flat list, chronologically ordered).

**Endpoint:**
```
GET /api/emails/po/{poNumber}
```

**Parameters:**
- `poNumber` (path) - The PO number (e.g., `PO-2025-0001`)

**Example Request:**
```bash
curl http://localhost:8080/api/emails/po/PO-2025-0001
```

**Response:**
```json
[
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "messageId": "<abc123@gmail.com>",
    "parentMessageId": null,
    "fromEmail": "john.doe@company.com",
    "toEmail": "procurement@company.com",
    "subject": "Need a new mouse",
    "cleanedBody": "Hi, I need a wireless mouse for my workstation. Bluetooth preferred.",
    "rawContent": "<html><body>Hi,<br>I need a wireless mouse...</body></html>",
    "systemMessage": false,
    "procurementRelated": true,
    "processingState": "RECOMMENDATIONS_SENT",
    "receivedAt": "2025-11-05T10:30:00",
    "createdAt": "2025-11-05T10:30:05",
    "depthLevel": 0,
    "metadata": null
  },
  {
    "id": 2,
    "poNumber": "PO-2025-0001",
    "messageId": "<system-PO-2025-0001-1730808010000@procurement.company.com>",
    "parentMessageId": "<abc123@gmail.com>",
    "fromEmail": "procurement@company.com",
    "toEmail": "john.doe@company.com",
    "subject": "Re: Need a new mouse - Recommendations",
    "cleanedBody": "Here are some available options:\n1. Logitech MX Master 3 - $85.00\n2. Razer DeathAdder V2 - $65.00",
    "rawContent": "Here are some available options:\n1. Logitech MX Master 3 - $85.00\n2. Razer DeathAdder V2 - $65.00",
    "systemMessage": true,
    "procurementRelated": true,
    "processingState": "RECOMMENDATIONS_SENT",
    "receivedAt": "2025-11-05T10:30:10",
    "createdAt": "2025-11-05T10:30:10",
    "depthLevel": 1,
    "metadata": null
  }
]
```

**Use Case:** Display complete email conversation in UI

---

### 2. Get Email Thread Hierarchy

Retrieve emails in nested structure showing parent-child relationships.

**Endpoint:**
```
GET /api/emails/po/{poNumber}/hierarchy
```

**Parameters:**
- `poNumber` (path) - The PO number

**Example Request:**
```bash
curl http://localhost:8080/api/emails/po/PO-2025-0001/hierarchy
```

**Response:**
```json
[
  {
    "email": {
      "id": 1,
      "poNumber": "PO-2025-0001",
      "messageId": "<abc123@gmail.com>",
      "fromEmail": "john.doe@company.com",
      "toEmail": "procurement@company.com",
      "subject": "Need a new mouse",
      "cleanedBody": "Hi, I need a wireless mouse for my workstation.",
      "receivedAt": "2025-11-05T10:30:00",
      "depthLevel": 0,
      "systemMessage": false,
      "procurementRelated": true
    },
    "children": [
      {
        "email": {
          "id": 2,
          "poNumber": "PO-2025-0001",
          "fromEmail": "procurement@company.com",
          "subject": "Re: Need a new mouse - Recommendations",
          "receivedAt": "2025-11-05T10:30:10",
          "depthLevel": 1,
          "systemMessage": true
        },
        "children": [
          {
            "email": {
              "id": 3,
              "fromEmail": "john.doe@company.com",
              "subject": "Re: Need a new mouse - Confirmation",
              "cleanedBody": "I'll take the Logitech MX Master 3",
              "receivedAt": "2025-11-05T11:00:00",
              "depthLevel": 2
            },
            "children": []
          }
        ]
      }
    ]
  }
]
```

**Use Case:** Display threaded conversation with indentation in UI

---

### 3. Get Email by Message ID

Retrieve a specific email by its unique message ID.

**Endpoint:**
```
GET /api/emails/message/{messageId}
```

**Parameters:**
- `messageId` (path) - The email message ID (URL encoded)

**Example Request:**
```bash
# Message ID must be URL encoded
curl http://localhost:8080/api/emails/message/%3Cabc123@gmail.com%3E
```

**Response:**
```json
{
  "id": 1,
  "poNumber": "PO-2025-0001",
  "messageId": "<abc123@gmail.com>",
  "parentMessageId": null,
  "fromEmail": "john.doe@company.com",
  "toEmail": "procurement@company.com",
  "subject": "Need a new mouse",
  "cleanedBody": "Hi, I need a wireless mouse for my workstation.",
  "rawContent": "<html><body>Hi,<br>I need a wireless mouse...</body></html>",
  "systemMessage": false,
  "procurementRelated": true,
  "processingState": "RECOMMENDATIONS_SENT",
  "receivedAt": "2025-11-05T10:30:00",
  "createdAt": "2025-11-05T10:30:05",
  "depthLevel": 0
}
```

**Use Case:** View details of a specific email

---

### 4. Get Emails by Sender

Retrieve all emails sent by a specific person.

**Endpoint:**
```
GET /api/emails/sender/{email}
```

**Parameters:**
- `email` (path) - The sender's email address

**Example Request:**
```bash
curl http://localhost:8080/api/emails/sender/john.doe@company.com
```

**Response:**
```json
[
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "messageId": "<abc123@gmail.com>",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a new mouse",
    "receivedAt": "2025-11-05T10:30:00",
    "processingState": "RECOMMENDATIONS_SENT"
  },
  {
    "id": 3,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Re: Need a new mouse - Confirmation",
    "receivedAt": "2025-11-05T11:00:00",
    "processingState": "CONFIRMATION_RECEIVED"
  },
  {
    "id": 7,
    "poNumber": "PO-2025-0002",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a laptop",
    "receivedAt": "2025-11-05T14:20:00",
    "processingState": "INVENTORY_CHECKED"
  }
]
```

**Use Case:** View all emails from a specific user

---

### 5. Get Procurement Emails Only

Filter to show only procurement-related emails.

**Endpoint:**
```
GET /api/emails/procurement
```

**Example Request:**
```bash
curl http://localhost:8080/api/emails/procurement
```

**Response:**
```json
[
  {
    "id": 5,
    "poNumber": "PO-2025-0003",
    "fromEmail": "jane.smith@company.com",
    "subject": "Request for monitors",
    "receivedAt": "2025-11-05T09:15:00",
    "procurementRelated": true,
    "processingState": "PO_GENERATED"
  },
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a new mouse",
    "receivedAt": "2025-11-05T10:30:00",
    "procurementRelated": true,
    "processingState": "RECOMMENDATIONS_SENT"
  }
]
```

**Use Case:** Dashboard showing only procurement requests

---

### 6. Get Emails by Processing State

Filter emails by their current processing state.

**Endpoint:**
```
GET /api/emails/state/{state}
```

**Parameters:**
- `state` (path) - Processing state (see states below)

**Available States:**
- `EMAIL_RECEIVED` - Email just arrived
- `CLASSIFIED` - Email classified by AI
- `RECOMMENDATIONS_SENT` - Recommendations sent to user
- `CONFIRMATION_RECEIVED` - User confirmed selection
- `PO_GENERATED` - Purchase order created
- `COMPLETION_SENT` - Process completed
- `NOT_PROCUREMENT` - Not procurement-related
- `ERROR` - Error occurred

**Example Request:**
```bash
# Get all emails waiting for recommendations
curl http://localhost:8080/api/emails/state/RECOMMENDATIONS_SENT

# Get non-procurement emails
curl http://localhost:8080/api/emails/state/NOT_PROCUREMENT

# Get error emails
curl http://localhost:8080/api/emails/state/ERROR
```

**Response:**
```json
[
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a new mouse",
    "processingState": "RECOMMENDATIONS_SENT",
    "receivedAt": "2025-11-05T10:30:00"
  }
]
```

**Use Case:** Monitor emails in specific workflow stages

---

### 7. Get Recent Emails

Retrieve emails from the last N days.

**Endpoint:**
```
GET /api/emails/recent
```

**Query Parameters:**
- `days` (optional, default: 7) - Number of days to look back

**Example Request:**
```bash
# Last 7 days (default)
curl http://localhost:8080/api/emails/recent

# Last 30 days
curl "http://localhost:8080/api/emails/recent?days=30"

# Last 24 hours
curl "http://localhost:8080/api/emails/recent?days=1"
```

**Response:**
```json
[
  {
    "id": 12,
    "poNumber": "PO-2025-0005",
    "fromEmail": "alice.johnson@company.com",
    "subject": "Keyboard request",
    "receivedAt": "2025-11-05T16:45:00",
    "processingState": "EMAIL_RECEIVED"
  },
  {
    "id": 7,
    "poNumber": "PO-2025-0002",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a laptop",
    "receivedAt": "2025-11-05T14:20:00",
    "processingState": "INVENTORY_CHECKED"
  }
]
```

**Use Case:** Recent activity dashboard

---

### 8. Get All PO Numbers

List all unique PO numbers (email threads).

**Endpoint:**
```
GET /api/emails/po-numbers
```

**Example Request:**
```bash
curl http://localhost:8080/api/emails/po-numbers
```

**Response:**
```json
[
  "PO-2025-0005",
  "PO-2025-0004",
  "PO-2025-0003",
  "PO-2025-0002",
  "PO-2025-0001"
]
```

**Use Case:** List all email conversations

---

### 9. Get Thread Statistics

Get detailed statistics for a specific email thread.

**Endpoint:**
```
GET /api/emails/po/{poNumber}/stats
```

**Parameters:**
- `poNumber` (path) - The PO number

**Example Request:**
```bash
curl http://localhost:8080/api/emails/po/PO-2025-0001/stats
```

**Response:**
```json
{
  "totalEmails": 5,
  "procurementEmails": 5,
  "systemEmails": 2,
  "participants": [
    "john.doe@company.com",
    "procurement@company.com"
  ],
  "participantCount": 2,
  "firstEmail": "2025-11-05T10:30:00",
  "lastEmail": "2025-11-05T12:00:00",
  "stateBreakdown": {
    "EMAIL_RECEIVED": 1,
    "RECOMMENDATIONS_SENT": 2,
    "CONFIRMATION_RECEIVED": 1,
    "COMPLETION_SENT": 1
  }
}
```

**Use Case:** Thread overview and analytics

---

### 10. Search Emails

Search emails by subject or body content.

**Endpoint:**
```
GET /api/emails/search
```

**Query Parameters:**
- `query` (required) - Search term

**Example Request:**
```bash
# Search for emails about mice
curl "http://localhost:8080/api/emails/search?query=mouse"

# Search for emails about laptops
curl "http://localhost:8080/api/emails/search?query=laptop"

# Search for specific user
curl "http://localhost:8080/api/emails/search?query=john.doe"
```

**Response:**
```json
[
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a new mouse",
    "cleanedBody": "Hi, I need a wireless mouse for my workstation.",
    "receivedAt": "2025-11-05T10:30:00",
    "processingState": "RECOMMENDATIONS_SENT"
  },
  {
    "id": 2,
    "poNumber": "PO-2025-0001",
    "fromEmail": "procurement@company.com",
    "subject": "Re: Need a new mouse - Recommendations",
    "cleanedBody": "Here are some available options:\n1. Logitech MX Master 3...",
    "receivedAt": "2025-11-05T10:30:10",
    "systemMessage": true
  }
]
```

**Use Case:** Full-text search across all emails

---

## 📊 Response Fields Reference

### EmailThread Object

| Field | Type | Description |
|-------|------|-------------|
| `id` | Long | Unique database ID |
| `poNumber` | String | PO number linking this email to thread |
| `messageId` | String | Original email message ID from headers |
| `parentMessageId` | String | Parent email message ID (for replies) |
| `fromEmail` | String | Sender's email address |
| `toEmail` | String | Recipient's email address |
| `subject` | String | Email subject line |
| `cleanedBody` | String | Cleaned email body (HTML removed, signatures stripped) |
| `rawContent` | String | Original email content with all formatting |
| `systemMessage` | Boolean | True if email is system-generated |
| `procurementRelated` | Boolean | True if procurement-related |
| `processingState` | String | Current workflow state |
| `receivedAt` | DateTime | When email was received |
| `createdAt` | DateTime | When record was created in database |
| `depthLevel` | Integer | Thread depth (0=root, 1=reply, 2=reply to reply) |
| `metadata` | String | Additional metadata (JSON) |

---

## 🔄 Email Processing States

```
EMAIL_RECEIVED
    ↓
CLASSIFIED
    ↓
CONTEXT_GATHERED
    ↓
INVENTORY_CHECKED
    ↓
RECOMMENDATIONS_SENT
    ↓
CONFIRMATION_RECEIVED
    ↓
PO_GENERATED
    ↓
ACCOUNTING_UPDATED
    ↓
COMPLETION_SENT
```

**Alternative Paths:**
- `NOT_PROCUREMENT` - Email is not procurement-related
- `USER_NOT_FOUND` - Sender not registered
- `ERROR` - Processing error occurred

---

## 💡 Usage Examples

### Example 1: Display Email Conversation

```javascript
// Fetch thread hierarchy
async function displayConversation(poNumber) {
  const response = await fetch(`/api/emails/po/${poNumber}/hierarchy`);
  const thread = await response.json();
  
  // Render nested structure
  function renderThread(node, depth = 0) {
    const indent = depth * 20;
    return `
      <div style="margin-left: ${indent}px; padding: 10px; border-left: 2px solid #ccc;">
        <strong>${node.email.fromEmail}</strong>
        <span>${new Date(node.email.receivedAt).toLocaleString()}</span>
        <p>${node.email.cleanedBody}</p>
        ${node.children.map(child => renderThread(child, depth + 1)).join('')}
      </div>
    `;
  }
  
  document.getElementById('thread').innerHTML = 
    thread.map(t => renderThread(t)).join('');
}
```

### Example 2: Monitor Pending Requests

```javascript
// Get all emails waiting for user confirmation
async function getPendingRequests() {
  const response = await fetch('/api/emails/state/RECOMMENDATIONS_SENT');
  const pending = await response.json();
  
  console.log(`${pending.length} requests waiting for confirmation`);
  return pending;
}
```

### Example 3: User Activity Dashboard

```javascript
// Get all conversations for a user
async function getUserActivity(email) {
  const [emails, stats] = await Promise.all([
    fetch(`/api/emails/sender/${email}`).then(r => r.json()),
    // Get stats for each unique PO number
    [...new Set(emails.map(e => e.poNumber))]
      .map(po => fetch(`/api/emails/po/${po}/stats`).then(r => r.json()))
  ]);
  
  return {
    totalEmails: emails.length,
    threads: stats.length,
    stats: await Promise.all(stats)
  };
}
```

### Example 4: Recent Activity Feed

```javascript
// Get recent emails with stats
async function getActivityFeed(days = 7) {
  const recent = await fetch(`/api/emails/recent?days=${days}`)
    .then(r => r.json());
  
  // Group by PO number
  const threads = {};
  recent.forEach(email => {
    if (!threads[email.poNumber]) {
      threads[email.poNumber] = [];
    }
    threads[email.poNumber].push(email);
  });
  
  return threads;
}
```

---

## 🎯 Integration Patterns

### Pattern 1: Conversation View
```
1. Fetch thread hierarchy: GET /api/emails/po/{poNumber}/hierarchy
2. Display nested emails with indentation based on depthLevel
3. Show raw content on expand, cleaned body by default
4. Update UI when processing state changes
```

### Pattern 2: Inbox View
```
1. Fetch recent emails: GET /api/emails/recent?days=30
2. Group by PO number (one row per conversation)
3. Show latest email in each thread
4. Badge showing unread/pending count
```

### Pattern 3: Search & Filter
```
1. User enters search term
2. Call: GET /api/emails/search?query={term}
3. Display results grouped by thread
4. Click result → navigate to full conversation
```

### Pattern 4: Status Dashboard
```
1. Fetch counts for each state
2. For each state: GET /api/emails/state/{state}
3. Display cards with counts and links
4. Real-time updates via polling or WebSocket
```

---

## 🚀 Quick Start Testing

Save this as `test-email-api.sh`:

```bash
#!/bin/bash
BASE_URL="http://localhost:8080/api/emails"

echo "=== Get All PO Numbers ==="
curl -s "$BASE_URL/po-numbers" | jq '.'

echo -e "\n=== Get Thread Hierarchy for First PO ==="
PO=$(curl -s "$BASE_URL/po-numbers" | jq -r '.[0]')
curl -s "$BASE_URL/po/$PO/hierarchy" | jq '.'

echo -e "\n=== Get Thread Stats ==="
curl -s "$BASE_URL/po/$PO/stats" | jq '.'

echo -e "\n=== Recent Emails (Last 7 Days) ==="
curl -s "$BASE_URL/recent?days=7" | jq '.[] | {poNumber, fromEmail, subject, receivedAt}'

echo -e "\n=== Procurement Emails Only ==="
curl -s "$BASE_URL/procurement" | jq '.[] | {poNumber, subject, processingState}'

echo -e "\n=== Search for 'mouse' ==="
curl -s "$BASE_URL/search?query=mouse" | jq '.[] | {poNumber, subject, cleanedBody}'
```

Make executable and run:
```bash
chmod +x test-email-api.sh
./test-email-api.sh
```

---

## ⚠️ Error Responses

### 404 Not Found
```json
{
  "timestamp": "2025-11-05T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Email thread not found for PO: PO-2025-9999",
  "path": "/api/emails/po/PO-2025-9999"
}
```

### 400 Bad Request
```json
{
  "timestamp": "2025-11-05T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Query parameter 'query' is required",
  "path": "/api/emails/search"
}
```

---

## 📝 Notes

1. **Performance**: For large threads (>100 emails), use flat list endpoint instead of hierarchy
2. **Caching**: Consider caching thread hierarchy results in UI
3. **Real-time**: Poll `/api/emails/recent` or implement WebSocket for live updates
4. **Search**: Search is case-insensitive and searches both subject and body
5. **Message IDs**: Must be URL encoded when used in path parameters

---

## 🔗 Related Documentation

- [Complete API Documentation](API_EXAMPLES.md) - All system APIs
- [Email Thread Implementation](EMAIL_THREAD_IMPLEMENTATION.md) - Technical details
- [Procurement API](API_EXAMPLES.md#procurement-apis) - Purchase order endpoints
- [Inventory API](API_EXAMPLES.md#inventory-apis) - Inventory management

---

**Last Updated**: November 5, 2025  
**API Version**: 1.0.0
