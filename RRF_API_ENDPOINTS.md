# RRF API Endpoints - Updated

## Overview
This document provides the updated list of RRF (Request for Requisition) API endpoints with PDF support.

## Endpoints

### 1. Get RRF as JSON
Returns the complete RRF object as JSON.

```bash
curl -X GET "http://localhost:8080/api/rrf/PO-2025-0248-BUE"
```

**Response:** JSON object with all RRF fields

---

### 2. Get RRF Content as Plain Text
Returns only the RRF document content as plain text.

```bash
curl -X GET "http://localhost:8080/api/rrf/PO-2025-0248-BUE/content"
```

**Response:** Plain text content

---

### 3. Download RRF as PDF ⭐ NEW
Downloads the RRF as a professionally formatted PDF document.

```bash
curl -X GET "http://localhost:8080/api/rrf/PO-2025-0248-BUE/pdf" \
     --output RRF_PO-2025-0248-BUE.pdf
```

**OR** open in browser:
```
http://localhost:8080/api/rrf/PO-2025-0248-BUE/pdf
```

**Response:** PDF file download

**PDF Features:**
- Professional header with company branding
- Requester information section
- Item details with specifications
- Complete RRF content
- Signature blocks for approvals
- System-generated disclaimer

---

### 4. Download RRF as Text File
Downloads the RRF content as a text file.

```bash
curl -X GET "http://localhost:8080/api/rrf/PO-2025-0248-BUE/download" \
     --output RRF_PO-2025-0248-BUE.txt
```

**Response:** Text file download

---

### 5. Update RRF Status
Updates the status of an RRF document.

```bash
curl -X PUT "http://localhost:8080/api/rrf/PO-2025-0248-BUE/status?status=APPROVED"
```

**Response:** Success/error message

---

## Quick Reference

| Format | Endpoint | Description |
|--------|----------|-------------|
| JSON | `/api/rrf/{poNumber}` | Full RRF object |
| Text | `/api/rrf/{poNumber}/content` | Content only |
| PDF | `/api/rrf/{poNumber}/pdf` | Professional PDF ⭐ |
| Text File | `/api/rrf/{poNumber}/download` | Text file download |

## Examples

### Get PDF in Browser
Simply open in your browser:
```
http://localhost:8080/api/rrf/PO-2025-0248-BUE/pdf
```

### Download PDF via cURL
```bash
curl -X GET "http://localhost:8080/api/rrf/PO-2025-0248-BUE/pdf" \
     --output RRF.pdf && open RRF.pdf  # macOS
```

### Save and View PDF
```bash
# Download
curl "http://localhost:8080/api/rrf/PO-2025-0248-BUE/pdf" > rrf.pdf

# View (macOS)
open rrf.pdf

# View (Linux)
xdg-open rrf.pdf

# View (Windows)
start rrf.pdf
```

## Setup Steps

1. **Apply Database Fix** (if you haven't already):
   ```bash
   # Connect to PostgreSQL and run:
   psql -U postgres -d procurement_db -f fix-rrf-lob-issue.sql
   ```

2. **Rebuild Application**:
   ```bash
   ./gradlew clean build
   ```

3. **Restart Application**:
   ```bash
   ./gradlew bootRun
   ```

4. **Test PDF Endpoint**:
   ```bash
   curl "http://localhost:8080/api/rrf/PO-2025-0248-BUE/pdf" --output test.pdf
   ```

## Notes

- The PDF endpoint generates a professional document with proper formatting
- All GET endpoints now have `@Transactional(readOnly = true)` to handle database operations properly
- The LOB issue is fixed by removing `@Lob` annotations from the entity
- PDF includes: header, requester info, item details, specifications, and signature blocks
