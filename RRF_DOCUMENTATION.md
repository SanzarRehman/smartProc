# RRF (Request for Requisition) Document Generation

## Overview

The system automatically generates a professional Request for Requisition (RRF) document using AI/LLM when a new procurement request is made (not from inventory). This document is stored in the database and can be retrieved, viewed, and downloaded via REST API.

---

## When RRF is Generated

RRF documents are automatically generated when:
- User confirms a **new purchase** (not from existing inventory)
- Before sending to human approval workflow
- The PO number is assigned to the RRF for tracking

---

## RRF Content

The LLM-generated RRF includes:

### 1. **Header Section**
- Document Type: REQUEST FOR REQUISITION
- RRF Number (PO Number)
- Date
- Company letterhead style

### 2. **Requester Information**
- Name
- Email
- Department
- Role

### 3. **Item Details**
- Item Name
- Category/Type
- Quantity
- Specifications (RAM, CPU, storage, etc.)
- Estimated Cost

### 4. **Business Justification**
- AI-generated explanation of why the purchase is necessary
- Based on request details and notes

### 5. **Approval Sections**
- Signature blocks for:
  - Requester
  - Department Head
  - Finance Approval

### 6. **Terms & Conditions**
- Standard procurement terms

---

## Database Schema

```sql
CREATE TABLE request_for_requisition (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(50) UNIQUE NOT NULL,
    requester_email VARCHAR(255),
    requester_name VARCHAR(255),
    item_name VARCHAR(255),
    item_type VARCHAR(100),
    quantity INTEGER,
    estimated_amount DECIMAL(15,2),
    rrf_content TEXT,              -- The full LLM-generated document
    specifications TEXT,            -- JSON string of specs
    justification TEXT,
    additional_notes TEXT,
    status VARCHAR(50),             -- DRAFT, PENDING_APPROVAL, APPROVED, REJECTED
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

---

## REST API Endpoints

### 1. Get RRF by PO Number

**Endpoint:** `GET /api/rrf/{poNumber}`

**Example:**
```bash
curl http://localhost:8080/api/rrf/PO-2025-0001
```

**Response:**
```json
{
  "id": 1,
  "poNumber": "PO-2025-0001",
  "requesterEmail": "john.doe@company.com",
  "requesterName": "John Doe",
  "itemName": "Dell XPS 15 Laptop",
  "itemType": "laptop",
  "quantity": 1,
  "estimatedAmount": 1500.00,
  "rrfContent": "REQUEST FOR REQUISITION\n\nRRF Number: PO-2025-0001\n...",
  "specifications": "{\"ram\":\"16GB\",\"storage\":\"512GB SSD\"}",
  "status": "PENDING_APPROVAL",
  "createdAt": "2025-11-09T10:30:00",
  "updatedAt": "2025-11-09T10:30:00"
}
```

---

### 2. Get RRF Content (Plain Text)

**Endpoint:** `GET /api/rrf/{poNumber}/content`

**Example:**
```bash
curl http://localhost:8080/api/rrf/PO-2025-0001/content
```

**Response:**
```text
                    REQUEST FOR REQUISITION
                    
RRF Number: PO-2025-0001
Date: November 09, 2025

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

REQUESTER INFORMATION:
  Name:       John Doe
  Email:      john.doe@company.com
  Department: Engineering
  Role:       EMPLOYEE

ITEM DETAILS:
  Item Name:      Dell XPS 15 Laptop
  Category:       Laptop
  Quantity:       1 unit(s)
  Estimated Cost: $1,500.00

SPECIFICATIONS:
  • RAM: 16GB
  • Storage: 512GB SSD
  • Processor: Intel i7

BUSINESS JUSTIFICATION:
  This laptop is required for the new software development team member
  starting next month. The specifications meet our standard development
  requirements and are necessary for running our development tools and
  virtual machines efficiently.

...
```

---

### 3. Download RRF Document

**Endpoint:** `GET /api/rrf/{poNumber}/download`

**Example:**
```bash
curl -O http://localhost:8080/api/rrf/PO-2025-0001/download
```

**Response:**
- Downloads file: `RRF_PO-2025-0001.txt`
- Content-Type: `text/plain`
- Content-Disposition: `attachment`

---

### 4. Update RRF Status

**Endpoint:** `PUT /api/rrf/{poNumber}/status?status={status}`

**Example:**
```bash
curl -X PUT "http://localhost:8080/api/rrf/PO-2025-0001/status?status=APPROVED"
```

**Response:**
```json
{
  "message": "RRF status updated successfully"
}
```

**Valid Status Values:**
- `DRAFT`
- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`

---

## Workflow Integration

### Email Processing Flow with RRF:

```
1. User sends email: "I need a laptop"
   ↓
2. System checks inventory
   ↓
3. If no inventory match:
   - System sends: "We'll purchase a new one, confirm?"
   ↓
4. User confirms: "Yes, proceed"
   ↓
5. System generates PO number: PO-2025-0001
   ↓
6. ** System generates RRF document using LLM **
   ↓
7. Saves RRF to database
   ↓
8. Sends to human approval workflow
   ↓
9. When approved:
   - Updates GL
   - Sends confirmation email
   - RRF status → APPROVED
```

---

## LLM Prompt Structure

The system sends a detailed prompt to OpenRouter API (gpt-oss-120b model):

```
Generate a professional Request for Requisition (RRF) document with:

Document Type: REQUEST FOR REQUISITION
RRF Number: PO-2025-0001
Date: November 09, 2025

REQUESTER INFORMATION:
Name: John Doe
Email: john.doe@company.com
...

ITEM DETAILS:
Item Name: Laptop
Quantity: 1 unit(s)
...

Please format this as a formal RRF document with:
1. Professional header
2. Clear sections
3. Business justification
4. Signature blocks
5. Terms and conditions
...
```

---

## UI Integration Guide

### Display RRF in UI:

```javascript
// Fetch RRF document
async function fetchRRF(poNumber) {
  const response = await fetch(`/api/rrf/${poNumber}`);
  const rrf = await response.json();
  
  // Display metadata
  document.getElementById('po-number').textContent = rrf.poNumber;
  document.getElementById('requester').textContent = rrf.requesterName;
  document.getElementById('status').textContent = rrf.status;
  
  // Display formatted RRF content
  document.getElementById('rrf-content').textContent = rrf.rrfContent;
}

// Download RRF
function downloadRRF(poNumber) {
  window.location.href = `/api/rrf/${poNumber}/download`;
}

// Approve RRF
async function approveRRF(poNumber) {
  await fetch(`/api/rrf/${poNumber}/status?status=APPROVED`, {
    method: 'PUT'
  });
  alert('RRF approved!');
}
```

---

### Example React Component:

```jsx
function RRFViewer({ poNumber }) {
  const [rrf, setRRF] = useState(null);
  
  useEffect(() => {
    fetch(`/api/rrf/${poNumber}`)
      .then(res => res.json())
      .then(data => setRRF(data));
  }, [poNumber]);
  
  if (!rrf) return <div>Loading...</div>;
  
  return (
    <div className="rrf-viewer">
      <h2>Request for Requisition</h2>
      <div className="rrf-header">
        <p><strong>RRF Number:</strong> {rrf.poNumber}</p>
        <p><strong>Status:</strong> {rrf.status}</p>
        <p><strong>Requester:</strong> {rrf.requesterName}</p>
      </div>
      
      <pre className="rrf-content">{rrf.rrfContent}</pre>
      
      <div className="actions">
        <button onClick={() => window.open(`/api/rrf/${poNumber}/download`)}>
          Download PDF
        </button>
        <button onClick={() => updateStatus(poNumber, 'APPROVED')}>
          Approve
        </button>
      </div>
    </div>
  );
}
```

---

## Configuration

### OpenRouter API Settings

Update `application.yml`:

```yaml
gemini:
  api:
    url: https://openrouter.ai/api/v1/chat/completions
    key: ${OPENROUTER_API_KEY}
    model: openai/gpt-oss-120b
    max-retries: 3
```

### Environment Variable

```bash
export OPENROUTER_API_KEY=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
```

---

## Testing

### Test RRF Generation:

```bash
# 1. Send procurement email
# 2. Confirm new purchase
# 3. Wait for RRF generation
# 4. Check API

curl http://localhost:8080/api/rrf/PO-2025-0001

# 5. Download RRF
curl -O http://localhost:8080/api/rrf/PO-2025-0001/download

# 6. View content
curl http://localhost:8080/api/rrf/PO-2025-0001/content
```

---

## Error Handling

### If RRF Generation Fails:

- The system logs the error but **continues** with the approval process
- RRF generation failure does NOT block procurement
- Can manually retry RRF generation later if needed

### Error Response Example:

```json
{
  "error": "RRF not found for PO: PO-2025-9999"
}
```

---

## Future Enhancements

1. **PDF Generation**: Convert RRF to PDF format
2. **Email Attachment**: Attach RRF to approval emails
3. **Custom Templates**: Allow department-specific RRF templates
4. **Multi-language**: Support RRF generation in different languages
5. **Digital Signatures**: Integrate e-signature capability
6. **Version Control**: Track RRF revisions

---

## Summary

✅ **Automatic RRF generation** for new purchases  
✅ **AI-powered content** using OpenRouter API  
✅ **REST API** for retrieval and download  
✅ **Database storage** with status tracking  
✅ **UI-ready** with simple JSON/text responses  
✅ **Non-blocking** - failures don't stop procurement  

---

**Last Updated**: November 9, 2025  
**Version**: 1.0.0
