package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import com.moveinsync.settlement.dto.VendorInvoiceResponse;
import com.moveinsync.settlement.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/vendor-invoices")
@RequiredArgsConstructor
@Tag(name = "Vendor Invoices", description = "Endpoints for vendor invoice submission, intake validation, and lookup")
public class VendorInvoiceController {

    private final ReconciliationService reconciliationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Submit Vendor Invoice", description = "Submits an external vendor tax invoice for a billing cycle.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invoice registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid invoice payload"),
            @ApiResponse(responseCode = "404", description = "Vendor or Business Unit not found"),
            @ApiResponse(responseCode = "409", description = "Invoice number already exists for this vendor")
    })
    public ResponseEntity<VendorInvoiceResponse> submitInvoice(@Valid @RequestBody VendorInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reconciliationService.submitInvoice(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "List Vendor Invoices", description = "Retrieves all submitted vendor invoices optionally filtered by vendor and period with pagination.")
    public ResponseEntity<List<VendorInvoiceResponse>> listInvoices(
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) String billingPeriod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reconciliationService.listInvoices(vendorId, billingPeriod, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Get Vendor Invoice by ID", description = "Retrieves details of a specific vendor invoice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<VendorInvoiceResponse> getInvoiceById(@PathVariable UUID id) {
        return ResponseEntity.ok(reconciliationService.getInvoiceById(id));
    }
}
