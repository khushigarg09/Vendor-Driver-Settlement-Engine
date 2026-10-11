package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.ReconciliationResponse;
import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import com.moveinsync.settlement.dto.VendorInvoiceResponse;

import java.util.List;
import java.util.UUID;

public interface ReconciliationService {

    /**
     * Submits and registers an external vendor tax invoice.
     */
    VendorInvoiceResponse submitInvoice(VendorInvoiceRequest request);

    /**
     * Retrieves a vendor invoice by its unique identifier.
     */
    VendorInvoiceResponse getInvoiceById(UUID id);

    /**
     * Lists invoices optionally filtered by vendor ID and billing period.
     */
    List<VendorInvoiceResponse> listInvoices(UUID vendorId, String billingPeriod);

    /**
     * Lists invoices with pagination optionally filtered by vendor ID and billing period.
     */
    List<VendorInvoiceResponse> listInvoices(UUID vendorId, String billingPeriod, int page, int size);

    /**
     * Performs automated tolerance-based reconciliation between a settlement run and a vendor invoice.
     */
    ReconciliationResponse reconcile(ReconciliationRequest request, String reconciledBy);

    /**
     * Retrieves an existing reconciliation record by ID.
     */
    ReconciliationResponse getReconciliationById(UUID id);
}
