# API Documentation for UI Integration

This document provides examples of all GET API endpoints available for the Procurement Email Automation system. Use these endpoints to build your UI.

## Base URL
```
http://localhost:8080
```

---

## 📦 Inventory APIs

### 1. Get All Available Items
Retrieve all inventory items with available quantity > 0.

```bash
curl http://localhost:8080/api/inventory
```

**Response Example:**
```json
[
  {
    "id": "LAP-001",
    "name": "Dell XPS 15",
    "type": "laptop",
    "specifications": {
      "ram": "16GB",
      "storage": "512GB SSD",
      "processor": "Intel i7"
    },
    "availableQuantity": 10,
    "bookValue": 1200.00
  },
  {
    "id": "MON-001",
    "name": "Dell UltraSharp 27\"",
    "type": "monitor",
    "specifications": {
      "resolution": "4K",
      "size": "27 inch"
    },
    "availableQuantity": 15,
    "bookValue": 450.00
  },
  {
    "id": "ACC-003",
    "name": "Logitech MX Master 3",
    "type": "accessory",
    "specifications": {
      "subtype": "mouse",
      "connectivity": "Bluetooth"
    },
    "availableQuantity": 25,
    "bookValue": 85.00
  }
]
```

---

### 2. Get Item by ID
Retrieve a specific inventory item by its ID.

```bash
curl http://localhost:8080/api/inventory/LAP-001
```

**Response Example:**
```json
{
  "id": "LAP-001",
  "name": "Dell XPS 15",
  "type": "laptop",
  "specifications": {
    "ram": "16GB",
    "storage": "512GB SSD",
    "processor": "Intel i7"
  },
  "availableQuantity": 10,
  "bookValue": 1200.00
}
```

**404 Response (if not found):**
```json
{
  "timestamp": "2024-11-05T10:30:00",
  "status": 404,
  "error": "Not Found",
  "path": "/api/inventory/INVALID-ID"
}
```

---

### 3. Search Items by Type
Search inventory items by type and optional brand/model filters.

```bash
# Search for all laptops
curl "http://localhost:8080/api/inventory/search?type=laptop"

# Search for Dell laptops
curl "http://localhost:8080/api/inventory/search?type=laptop&brand=Dell"

# Search for mice (accessories)
curl "http://localhost:8080/api/inventory/search?type=mouse"

# Search for keyboards
curl "http://localhost:8080/api/inventory/search?type=keyboard"
```

**Response Example:**
```json
[
  {
    "id": "ACC-003",
    "name": "Logitech MX Master 3",
    "type": "accessory",
    "specifications": {
      "subtype": "mouse",
      "connectivity": "Bluetooth"
    },
    "availableQuantity": 25,
    "bookValue": 85.00
  },
  {
    "id": "ACC-004",
    "name": "Razer DeathAdder V2",
    "type": "accessory",
    "specifications": {
      "subtype": "mouse",
      "connectivity": "USB"
    },
    "availableQuantity": 20,
    "bookValue": 65.00
  }
]
```

---

### 4. Get Multiple Items by IDs
Retrieve multiple items in a single request.

```bash
curl "http://localhost:8080/api/inventory/batch?ids=LAP-001,MON-001,ACC-003"
```

**Response Example:**
```json
[
  {
    "id": "LAP-001",
    "name": "Dell XPS 15",
    "type": "laptop",
    "specifications": {"ram": "16GB", "storage": "512GB SSD"},
    "availableQuantity": 10,
    "bookValue": 1200.00
  },
  {
    "id": "MON-001",
    "name": "Dell UltraSharp 27\"",
    "type": "monitor",
    "specifications": {"resolution": "4K", "size": "27 inch"},
    "availableQuantity": 15,
    "bookValue": 450.00
  },
  {
    "id": "ACC-003",
    "name": "Logitech MX Master 3",
    "type": "accessory",
    "specifications": {"subtype": "mouse"},
    "availableQuantity": 25,
    "bookValue": 85.00
  }
]
```

---

### 5. Get Inventory Statistics
Get summary statistics about inventory.

```bash
curl http://localhost:8080/api/inventory/stats
```

**Response Example:**
```json
{
  "totalItems": 9,
  "totalQuantity": 120,
  "totalValue": 78500.00,
  "itemsByType": {
    "laptop": 3,
    "monitor": 3,
    "accessory": 3
  }
}
```

---

## 🛒 Procurement APIs

### 1. Get All Purchase Orders
Retrieve all purchase orders.

```bash
curl http://localhost:8080/api/procurement
```

**Response Example:**
```json
[
  {
    "poNumber": "PO-2024-001",
    "itemName": "Dell XPS 15",
    "itemType": "laptop",
    "specifications": {
      "ram": "16GB",
      "storage": "512GB SSD"
    },
    "quantity": 5,
    "amount": 6000.00,
    "requesterEmail": "john.doe@company.com",
    "requesterRole": "EMPLOYEE",
    "createdAt": "2024-11-01T10:00:00",
    "status": "APPROVED"
  }
]
```

---

### 2. Get Purchase Order by PO Number
Retrieve a specific purchase order.

```bash
curl http://localhost:8080/api/procurement/PO-2024-001
```

**Response Example:**
```json
{
  "poNumber": "PO-2024-001",
  "itemName": "Dell XPS 15",
  "itemType": "laptop",
  "specifications": {
    "ram": "16GB",
    "storage": "512GB SSD"
  },
  "quantity": 5,
  "amount": 6000.00,
  "requesterEmail": "john.doe@company.com",
  "requesterRole": "EMPLOYEE",
  "createdAt": "2024-11-01T10:00:00",
  "status": "APPROVED"
}
```

---

### 3. Get Purchase Orders by Requester
Get all purchase orders for a specific user.

```bash
curl http://localhost:8080/api/procurement/requester/john.doe@company.com
```

**Response Example:**
```json
[
  {
    "poNumber": "PO-2024-001",
    "itemName": "Dell XPS 15",
    "itemType": "laptop",
    "quantity": 5,
    "amount": 6000.00,
    "requesterEmail": "john.doe@company.com",
    "status": "APPROVED",
    "createdAt": "2024-11-01T10:00:00"
  },
  {
    "poNumber": "PO-2024-003",
    "itemName": "Logitech MX Master 3",
    "itemType": "accessory",
    "quantity": 2,
    "amount": 170.00,
    "requesterEmail": "john.doe@company.com",
    "status": "PENDING",
    "createdAt": "2024-11-03T14:30:00"
  }
]
```

---

### 4. Get Purchase Orders by Status
Filter purchase orders by status.

```bash
# Get pending orders
curl http://localhost:8080/api/procurement/status/PENDING

# Get approved orders
curl http://localhost:8080/api/procurement/status/APPROVED

# Get rejected orders
curl http://localhost:8080/api/procurement/status/REJECTED

# Get completed orders
curl http://localhost:8080/api/procurement/status/COMPLETED
```

**Response Example:**
```json
[
  {
    "poNumber": "PO-2024-003",
    "itemName": "Logitech MX Master 3",
    "itemType": "accessory",
    "quantity": 2,
    "amount": 170.00,
    "requesterEmail": "john.doe@company.com",
    "status": "PENDING",
    "createdAt": "2024-11-03T14:30:00"
  }
]
```

---

### 5. Get Recent Purchase Orders
Get purchase orders from the last N days.

```bash
# Last 30 days (default)
curl http://localhost:8080/api/procurement/recent

# Last 7 days
curl "http://localhost:8080/api/procurement/recent?days=7"

# Last 90 days
curl "http://localhost:8080/api/procurement/recent?days=90"
```

**Response Example:**
```json
[
  {
    "poNumber": "PO-2024-005",
    "itemName": "Dell UltraSharp 27\"",
    "itemType": "monitor",
    "quantity": 3,
    "amount": 1350.00,
    "requesterEmail": "jane.smith@company.com",
    "status": "APPROVED",
    "createdAt": "2024-11-04T09:15:00"
  }
]
```

---

### 6. Get Procurement Statistics
Get summary statistics about purchase orders.

```bash
curl http://localhost:8080/api/procurement/stats
```

**Response Example:**
```json
{
  "totalOrders": 25,
  "totalAmount": 45000.00,
  "ordersByStatus": {
    "PENDING": 5,
    "APPROVED": 15,
    "REJECTED": 2,
    "COMPLETED": 3
  },
  "ordersByType": {
    "laptop": 8,
    "monitor": 10,
    "accessory": 7
  }
}
```

---

## 💰 General Ledger APIs

### 1. Get All GL Entries
Retrieve all general ledger entries.

```bash
curl http://localhost:8080/api/gl
```

**Response Example:**
```json
[
  {
    "id": 1,
    "transactionType": "ALLOCATION",
    "accountDebit": "Equipment Expense",
    "accountCredit": "Inventory",
    "amount": 1200.00,
    "referenceNumber": "ALLOC-2024-001",
    "requesterEmail": "john.doe@company.com",
    "transactionDate": "2024-11-01T10:30:00",
    "description": "Allocation: Dell XPS 15 (1 units)"
  },
  {
    "id": 2,
    "transactionType": "PURCHASE",
    "accountDebit": "Inventory",
    "accountCredit": "Accounts Payable",
    "amount": 6000.00,
    "referenceNumber": "PO-2024-001",
    "requesterEmail": "john.doe@company.com",
    "transactionDate": "2024-11-01T11:00:00",
    "description": "Purchase Order: Dell XPS 15 (5 units)"
  }
]
```

---

### 2. Get GL Entry by ID
Retrieve a specific GL entry.

```bash
curl http://localhost:8080/api/gl/1
```

**Response Example:**
```json
{
  "id": 1,
  "transactionType": "ALLOCATION",
  "accountDebit": "Equipment Expense",
  "accountCredit": "Inventory",
  "amount": 1200.00,
  "referenceNumber": "ALLOC-2024-001",
  "requesterEmail": "john.doe@company.com",
  "transactionDate": "2024-11-01T10:30:00",
  "description": "Allocation: Dell XPS 15 (1 units)"
}
```

---

### 3. Get GL Entries by Reference Number
Get all GL entries for a specific PO or allocation.

```bash
# Get entries for a purchase order
curl http://localhost:8080/api/gl/reference/PO-2024-001

# Get entries for an allocation
curl http://localhost:8080/api/gl/reference/ALLOC-2024-001
```

**Response Example:**
```json
[
  {
    "id": 2,
    "transactionType": "PURCHASE",
    "accountDebit": "Inventory",
    "accountCredit": "Accounts Payable",
    "amount": 6000.00,
    "referenceNumber": "PO-2024-001",
    "requesterEmail": "john.doe@company.com",
    "transactionDate": "2024-11-01T11:00:00",
    "description": "Purchase Order: Dell XPS 15 (5 units)"
  }
]
```

---

### 4. Get GL Entries by Requester
Get all GL entries for a specific user.

```bash
curl http://localhost:8080/api/gl/requester/john.doe@company.com
```

**Response Example:**
```json
[
  {
    "id": 1,
    "transactionType": "ALLOCATION",
    "amount": 1200.00,
    "referenceNumber": "ALLOC-2024-001",
    "requesterEmail": "john.doe@company.com",
    "transactionDate": "2024-11-01T10:30:00"
  },
  {
    "id": 2,
    "transactionType": "PURCHASE",
    "amount": 6000.00,
    "referenceNumber": "PO-2024-001",
    "requesterEmail": "john.doe@company.com",
    "transactionDate": "2024-11-01T11:00:00"
  }
]
```

---

### 5. Get GL Entries by Transaction Type
Filter by ALLOCATION or PURCHASE.

```bash
# Get allocation entries
curl http://localhost:8080/api/gl/type/ALLOCATION

# Get purchase entries
curl http://localhost:8080/api/gl/type/PURCHASE
```

**Response Example:**
```json
[
  {
    "id": 1,
    "transactionType": "ALLOCATION",
    "accountDebit": "Equipment Expense",
    "accountCredit": "Inventory",
    "amount": 1200.00,
    "referenceNumber": "ALLOC-2024-001",
    "transactionDate": "2024-11-01T10:30:00"
  }
]
```

---

### 6. Get GL Entries by Date Range
Get entries within a specific date range.

```bash
curl "http://localhost:8080/api/gl/date-range?startDate=2024-11-01T00:00:00&endDate=2024-11-05T23:59:59"
```

**Response Example:**
```json
[
  {
    "id": 1,
    "transactionType": "ALLOCATION",
    "amount": 1200.00,
    "transactionDate": "2024-11-01T10:30:00"
  },
  {
    "id": 2,
    "transactionType": "PURCHASE",
    "amount": 6000.00,
    "transactionDate": "2024-11-01T11:00:00"
  }
]
```

---

### 7. Get Recent GL Entries
Get GL entries from the last N days.

```bash
# Last 30 days (default)
curl http://localhost:8080/api/gl/recent

# Last 7 days
curl "http://localhost:8080/api/gl/recent?days=7"
```

**Response Example:**
```json
[
  {
    "id": 5,
    "transactionType": "ALLOCATION",
    "amount": 450.00,
    "transactionDate": "2024-11-04T09:20:00"
  }
]
```

---

### 8. Get GL Statistics
Get summary statistics about GL entries.

```bash
curl http://localhost:8080/api/gl/stats
```

**Response Example:**
```json
{
  "totalEntries": 50,
  "totalAmount": 75000.00,
  "entriesByType": {
    "ALLOCATION": 30,
    "PURCHASE": 20
  },
  "amountByType": {
    "ALLOCATION": 25000.00,
    "PURCHASE": 50000.00
  }
}
```

---

## 🔍 Vector Search APIs (Semantic Similarity)

### 1. Search Similar Items (IDs only)
Get similar item IDs based on semantic search.

```bash
# Search for mouse
curl "http://localhost:8080/api/inventory/vector/search?query=mouse&limit=5"

# Search for laptop
curl "http://localhost:8080/api/inventory/vector/search?query=laptop&limit=5"

# Search for keyboard
curl "http://localhost:8080/api/inventory/vector/search?query=keyboard&limit=3"
```

**Response Example:**
```json
["ACC-003", "ACC-004"]
```

---

### 2. Search Similar Items (with scores)
Get similar items with similarity scores (only items with score >= 0.4 are returned).

```bash
curl "http://localhost:8080/api/inventory/vector/search/scores?query=mouse&limit=5"
```

**Response Example:**
```json
{
  "ACC-003": 0.89,
  "ACC-004": 0.85
}
```

---

### 3. Get Similar Items by Item ID
Find items similar to a specific inventory item.

```bash
# Find items similar to ACC-003 (Logitech MX Master 3 mouse)
curl http://localhost:8080/api/inventory/vector/similar/ACC-003?limit=3
```

**Response Example:**
```json
["ACC-004", "ACC-005"]
```

---

### 4. Get Similar Items with Details
Get full details of similar items.

```bash
curl http://localhost:8080/api/inventory/vector/similar/ACC-003/details?limit=3
```

**Response Example:**
```json
[
  {
    "id": "ACC-004",
    "name": "Razer DeathAdder V2",
    "type": "accessory",
    "specifications": {
      "subtype": "mouse",
      "connectivity": "USB"
    },
    "availableQuantity": 20,
    "bookValue": 65.00
  }
]
```

---

## 📊 UI Integration Tips

### For List Views
Use the "get all" endpoints with pagination if needed:
- `/api/inventory` - Display inventory catalog
- `/api/procurement` - Show all purchase orders
- `/api/gl` - Display ledger entries

### For Detail Views
Use the "by ID" endpoints:
- `/api/inventory/{itemId}` - Show item details
- `/api/procurement/{poNumber}` - Show PO details
- `/api/gl/{id}` - Show GL entry details

### For Dashboards
Use the "stats" endpoints:
- `/api/inventory/stats` - Inventory metrics
- `/api/procurement/stats` - Procurement metrics
- `/api/gl/stats` - Financial metrics

### For Search/Filter
Use the search and filter endpoints:
- `/api/inventory/search?type=laptop` - Filter by type
- `/api/procurement/status/PENDING` - Filter by status
- `/api/gl/type/ALLOCATION` - Filter by transaction type
- `/api/inventory/vector/search?query=mouse` - Semantic search

### For User-Specific Views
Use the "by requester" endpoints:
- `/api/procurement/requester/{email}` - User's orders
- `/api/gl/requester/{email}` - User's transactions

---

## � Email Thread APIs

### 1. Get Emails by PO Number
Get all emails in a thread (flat list, ordered by date).

```bash
curl http://localhost:8080/api/emails/po/PO-2025-0001
```

**Response Example:**
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
    "isSystemMessage": false,
    "isProcurementRelated": true,
    "processingState": "RECOMMENDATIONS_SENT",
    "receivedAt": "2025-11-05T10:30:00",
    "createdAt": "2025-11-05T10:30:05",
    "depthLevel": 0
  },
  {
    "id": 2,
    "poNumber": "PO-2025-0001",
    "messageId": "<system-PO-2025-0001-1730808010000@procurement.company.com>",
    "parentMessageId": "<abc123@gmail.com>",
    "fromEmail": "procurement@company.com",
    "toEmail": "john.doe@company.com",
    "subject": "Re: Need a new mouse - Recommendations",
    "cleanedBody": "Here are some available options:\n1. Logitech MX Master 3...",
    "rawContent": "Here are some available options:\n1. Logitech MX Master 3...",
    "isSystemMessage": true,
    "isProcurementRelated": true,
    "processingState": "RECOMMENDATIONS_SENT",
    "receivedAt": "2025-11-05T10:30:10",
    "createdAt": "2025-11-05T10:30:10",
    "depthLevel": 1
  }
]
```

---

### 2. Get Email Thread Hierarchy
Get nested email structure (parent-child relationships).

```bash
curl http://localhost:8080/api/emails/po/PO-2025-0001/hierarchy
```

**Response Example:**
```json
[
  {
    "email": {
      "id": 1,
      "poNumber": "PO-2025-0001",
      "fromEmail": "john.doe@company.com",
      "subject": "Need a new mouse",
      "cleanedBody": "Hi, I need a wireless mouse...",
      "receivedAt": "2025-11-05T10:30:00",
      "depthLevel": 0
    },
    "children": [
      {
        "email": {
          "id": 2,
          "poNumber": "PO-2025-0001",
          "fromEmail": "procurement@company.com",
          "subject": "Re: Need a new mouse - Recommendations",
          "receivedAt": "2025-11-05T10:30:10",
          "depthLevel": 1
        },
        "children": [
          {
            "email": {
              "id": 3,
              "fromEmail": "john.doe@company.com",
              "subject": "Re: Need a new mouse - Confirmation",
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

---

### 3. Get Email by Message ID
Retrieve a specific email.

```bash
curl http://localhost:8080/api/emails/message/%3Cabc123@gmail.com%3E
```

**Response Example:**
```json
{
  "id": 1,
  "poNumber": "PO-2025-0001",
  "messageId": "<abc123@gmail.com>",
  "fromEmail": "john.doe@company.com",
  "subject": "Need a new mouse",
  "cleanedBody": "Hi, I need a wireless mouse for my workstation.",
  "rawContent": "<html><body>Hi,<br>I need a wireless mouse...</body></html>",
  "isProcurementRelated": true,
  "processingState": "RECOMMENDATIONS_SENT",
  "receivedAt": "2025-11-05T10:30:00"
}
```

---

### 4. Get Emails by Sender
Get all emails from a specific person.

```bash
curl http://localhost:8080/api/emails/sender/john.doe@company.com
```

**Response Example:**
```json
[
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a new mouse",
    "receivedAt": "2025-11-05T10:30:00"
  },
  {
    "id": 3,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Re: Need a new mouse - Confirmation",
    "receivedAt": "2025-11-05T11:00:00"
  }
]
```

---

### 5. Get Procurement Emails Only
Filter to show only procurement-related emails.

```bash
curl http://localhost:8080/api/emails/procurement
```

---

### 6. Get Emails by Processing State
Filter emails by their processing state.

```bash
# Get all emails waiting for recommendations
curl http://localhost:8080/api/emails/state/RECOMMENDATIONS_SENT

# Get non-procurement emails
curl http://localhost:8080/api/emails/state/NOT_PROCUREMENT

# Get error emails
curl http://localhost:8080/api/emails/state/ERROR
```

---

### 7. Get Recent Emails
Get emails from the last N days.

```bash
# Last 7 days (default)
curl http://localhost:8080/api/emails/recent

# Last 30 days
curl "http://localhost:8080/api/emails/recent?days=30"
```

---

### 8. Get All PO Numbers
List all email threads/conversations.

```bash
curl http://localhost:8080/api/emails/po-numbers
```

**Response Example:**
```json
[
  "PO-2025-0005",
  "PO-2025-0004",
  "PO-2025-0003",
  "PO-2025-0002",
  "PO-2025-0001"
]
```

---

### 9. Get Thread Statistics
Get statistics for a specific email thread.

```bash
curl http://localhost:8080/api/emails/po/PO-2025-0001/stats
```

**Response Example:**
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

---

### 10. Search Emails
Search by subject or body content.

```bash
# Search for emails about mice
curl "http://localhost:8080/api/emails/search?query=mouse"

# Search for emails about laptops
curl "http://localhost:8080/api/emails/search?query=laptop"
```

**Response Example:**
```json
[
  {
    "id": 1,
    "poNumber": "PO-2025-0001",
    "fromEmail": "john.doe@company.com",
    "subject": "Need a new mouse",
    "cleanedBody": "Hi, I need a wireless mouse for my workstation.",
    "receivedAt": "2025-11-05T10:30:00"
  }
]
```

---

## �🚀 Testing All APIs

Run this script to test all endpoints:

```bash
#!/bin/bash
BASE_URL="http://localhost:8080"

echo "=== Testing Inventory APIs ==="
curl -s "$BASE_URL/api/inventory" | jq '.[] | {id, name, type, availableQuantity}'
curl -s "$BASE_URL/api/inventory/LAP-001" | jq '.'
curl -s "$BASE_URL/api/inventory/search?type=mouse" | jq '.'
curl -s "$BASE_URL/api/inventory/stats" | jq '.'

echo -e "\n=== Testing Procurement APIs ==="
curl -s "$BASE_URL/api/procurement" | jq '.[] | {poNumber, itemName, status}'
curl -s "$BASE_URL/api/procurement/status/PENDING" | jq '.'
curl -s "$BASE_URL/api/procurement/stats" | jq '.'

echo -e "\n=== Testing GL APIs ==="
curl -s "$BASE_URL/api/gl" | jq '.[] | {id, transactionType, amount}'
curl -s "$BASE_URL/api/gl/type/ALLOCATION" | jq '.'
curl -s "$BASE_URL/api/gl/stats" | jq '.'

echo -e "\n=== Testing Vector Search APIs ==="
curl -s "$BASE_URL/api/inventory/vector/search?query=mouse&limit=3" | jq '.'
curl -s "$BASE_URL/api/inventory/vector/search/scores?query=laptop&limit=3" | jq '.'

echo -e "\n=== Testing Email Thread APIs ==="
curl -s "$BASE_URL/api/emails/po-numbers" | jq '.'
curl -s "$BASE_URL/api/emails/procurement" | jq '.[] | {id, poNumber, fromEmail, subject, receivedAt}'
curl -s "$BASE_URL/api/emails/recent?days=7" | jq '.'
```

Save this as `test-apis.sh`, make it executable with `chmod +x test-apis.sh`, and run it!

---

## 📝 Email Processing Flow

### How Email Threads Work:

1. **Email Arrives** → System generates `PO-YYYY-NNNN` immediately
2. **Email Saved** → Raw + cleaned content stored in `email_threads` table
3. **Classification** → AI determines if procurement-related
4. **Processing State** → Saved in `email_processing_states` table
5. **All Emails Tracked** → Even non-procurement emails get PO number for audit trail

### PO Number Format:
- **PO-2025-0001** → Year + Sequential number
- Every email gets a unique PO number
- All replies in a thread share the same PO number
- System emails (recommendations, confirmations) also logged with PO number

### Email Cleaning Features:
- ✅ HTML stripped to plain text
- ✅ Email signatures removed
- ✅ Quoted reply text removed (lines starting with `>`)
- ✅ Reply headers removed (e.g., "On Mon, Nov 5...")
- ✅ Excessive whitespace cleaned
- ✅ Original raw content preserved in `rawContent` field

### Thread Hierarchy:
- **depth_level = 0** → Original email
- **depth_level = 1** → First reply
- **depth_level = 2** → Reply to reply
- Parent-child relationships maintained via `parent_message_id`
