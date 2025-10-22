package com.procurement.email.service;

import com.procurement.email.model.GeneralLedgerEntry;
import com.procurement.email.model.Item;
import com.procurement.email.model.PurchaseOrder;
import com.procurement.email.model.UserContext;
import com.procurement.email.repository.GeneralLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service responsible for simulating general ledger updates for procurement transactions.
 * This service handles accounting entries for both inventory allocations and new purchases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingAgentService {
    
    private final GeneralLedgerEntryRepository generalLedgerEntryRepository;
    
    private static final String TRANSACTION_TYPE_ALLOCATION = "ALLOCATION";
    private static final String TRANSACTION_TYPE_PURCHASE = "PURCHASE";
    private static final String ACCOUNT_FIXED_ASSETS = "Fixed Assets";
    private static final String ACCOUNT_INVENTORY = "Inventory";
    private static final String ACCOUNT_ACCOUNTS_PAYABLE = "Accounts Payable";
    
    /**
     * Records a general ledger entry for inventory allocation.
     * Creates a debit to Fixed Assets and credit to Inventory.
     * 
     * @param item the item being allocated from inventory
     * @param requester the user context of the requester
     */
    @Transactional
    public void recordInventoryAllocation(Item item, UserContext requester) {
        log.info("Recording inventory allocation for item: {} to requester: {}", 
                item.getName(), requester.getEmail());
        
        String allocationId = "ALLOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        GeneralLedgerEntry glEntry = new GeneralLedgerEntry();
        glEntry.setTransactionType(TRANSACTION_TYPE_ALLOCATION);
        glEntry.setAccountDebit(ACCOUNT_FIXED_ASSETS);
        glEntry.setAccountCredit(ACCOUNT_INVENTORY);
        glEntry.setAmount(item.getBookValue());
        glEntry.setReferenceNumber(allocationId);
        glEntry.setRequesterEmail(requester.getEmail());
        glEntry.setTransactionDate(LocalDateTime.now());
        glEntry.setDescription(String.format("Inventory allocation: %s (%s) to %s - Role: %s", 
                item.getName(), 
                item.getType(), 
                requester.getEmail(),
                requester.getRole()));
        
        generalLedgerEntryRepository.save(glEntry);
        
        log.info("Successfully recorded inventory allocation GL entry with reference: {}", allocationId);
    }
    
    /**
     * Records a general ledger entry for a purchase transaction.
     * Creates a debit to Fixed Assets and credit to Accounts Payable.
     * 
     * @param po the purchase order for the transaction
     */
    @Transactional
    public void recordPurchaseTransaction(PurchaseOrder po) {
        log.info("Recording purchase transaction for PO: {}", po.getPoNumber());
        
        GeneralLedgerEntry glEntry = new GeneralLedgerEntry();
        glEntry.setTransactionType(TRANSACTION_TYPE_PURCHASE);
        glEntry.setAccountDebit(ACCOUNT_FIXED_ASSETS);
        glEntry.setAccountCredit(ACCOUNT_ACCOUNTS_PAYABLE);
        glEntry.setAmount(po.getAmount());
        glEntry.setReferenceNumber(po.getPoNumber());
        glEntry.setRequesterEmail(po.getRequesterEmail());
        glEntry.setTransactionDate(LocalDateTime.now());
        glEntry.setDescription(String.format("Purchase transaction: %s (%s) - PO: %s - Vendor: Mock Vendor", 
                po.getItemName(), 
                po.getItemType(), 
                po.getPoNumber()));
        
        generalLedgerEntryRepository.save(glEntry);
        
        log.info("Successfully recorded purchase transaction GL entry for PO: {}", po.getPoNumber());
    }
}
