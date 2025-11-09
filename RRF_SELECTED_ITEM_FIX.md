# Fix: RRF Showing Multiple Items Instead of Selected Item

## Problem
When a user confirms a specific item from the inventory recommendations, the generated RRF document was showing multiple items or incorrect item information instead of just the selected/confirmed item.

## Root Cause
The RRF generation was using the original `ProcurementRequest` object which contained:
- The user's original request (possibly generic like "laptop")
- Multiple matched inventory items from AI analysis
- All candidate items from the recommendation

When the user confirmed a specific item (e.g., "Dell Latitude 5420"), we were not passing this selected item information to the RRF generator, so the LLM was generating a document with all the options instead of just the confirmed one.

## Solution

### 1. Updated `RRFService.java`
Added an overloaded method to accept the selected item:

```java
public RequestForRequisition generateAndSaveRRF(
    String poNumber,
    ProcurementRequest request,
    UserContext userContext,
    BigDecimal estimatedAmount,
    Item selectedItem  // NEW PARAMETER
)
```

**Key Changes:**
- If `selectedItem` is provided, use its name, ID, type, and description
- If `selectedItem` is null (new purchase), use the request data
- Added instruction to LLM: "Generate the RRF for ONLY the ONE selected/confirmed item"

### 2. Updated `EmailProcessingService.java`
Pass the selected item when generating RRF:

**For Inventory Allocation:**
```java
// Now passes selectedItem from confirmation
rrfService.generateAndSaveRRF(poNumber, request, userContext, 
                              purchaseOrder.getAmount(), selectedItem);
```

**For New Purchase:**
```java
// Passes null since there's no inventory item
rrfService.generateAndSaveRRF(poNumber, request, userContext, 
                              purchaseOrder.getAmount(), null);
```

### 3. Enhanced LLM Prompt
The prompt now includes:
- **Item ID** (when selected from inventory)
- **Item Description** (when available)
- **Explicit instruction**: "IMPORTANT: Generate the RRF for ONLY the ONE selected/confirmed item above. Do NOT include multiple items or options."

## Before vs After

### Before Fix
**User confirms:** "I want the Dell Latitude 5420 (ID: LAP-001)"

**RRF Generated:**
```
ITEM DETAILS:
Item Name: Laptop
Options:
1. Dell Latitude 5420 (LAP-001)
2. HP EliteBook 840 (LAP-002)
3. Lenovo ThinkPad T14 (LAP-003)
```
❌ Shows all options instead of just the confirmed one

### After Fix
**User confirms:** "I want the Dell Latitude 5420 (ID: LAP-001)"

**RRF Generated:**
```
ITEM DETAILS:
Item Name: Dell Latitude 5420
Item ID: LAP-001
Category: Laptop
Description: 14" business laptop, Intel i5, 16GB RAM
Quantity: 1 unit(s)
```
✅ Shows only the confirmed item

## Testing

### Test Case 1: Inventory Allocation
1. Send procurement request: "Need a laptop with 16GB RAM"
2. System recommends 3 laptops
3. User confirms: "I want LAP-001"
4. Check RRF: Should show ONLY Dell Latitude 5420, not all 3 options

### Test Case 2: New Purchase
1. Send procurement request: "Need 10 ergonomic chairs"
2. System finds no inventory match
3. User confirms: "Yes, proceed with new purchase"
4. Check RRF: Should show the requested item details, not multiple options

### Verification Commands
```bash
# Get RRF as JSON
curl http://localhost:8080/api/rrf/PO-2025-XXXX

# View HTML
open http://localhost:8080/api/rrf/PO-2025-XXXX/html

# Check item name and ID are specific, not generic
```

## Database Impact
No database schema changes required. Only the content of `rrf_content` field will be different (more specific).

## Files Modified
1. `RRFService.java`
   - Added `Item selectedItem` parameter to generation methods
   - Updated prompt to include item ID and description
   - Added explicit instruction for single-item RRF

2. `EmailProcessingService.java`
   - Pass `confirmation.getSelectedItem()` for inventory allocation
   - Pass `null` for new purchases
   - Updated both RRF generation calls

## Benefits
✅ **Accurate RRFs**: Documents now reflect exactly what was approved  
✅ **Less Confusion**: No ambiguity about which item is being purchased  
✅ **Better Audit Trail**: Clear record of specific item confirmed  
✅ **Improved Compliance**: RRF matches the actual procurement decision  

## Notes
- The fix maintains backward compatibility (selectedItem can be null)
- LLM receives clear instruction to generate single-item RRF
- Works for both inventory allocation and new purchase scenarios
- No changes needed to database schema or API endpoints
