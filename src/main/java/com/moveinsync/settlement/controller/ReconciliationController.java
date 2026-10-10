package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.ReconciliationResponse;
import com.moveinsync.settlement.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/reconciliations")
@RequiredArgsConstructor
@Tag(name = "Reconciliations", description = "Endpoints for automated vendor invoice vs settlement run reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Reconcile Settlement Run with Vendor Invoice", 
               description = "Compares system calculated net payout against vendor claimed invoice amount using mathematical tolerance threshold.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconciliation completed or idempotent replay returned"),
            @ApiResponse(responseCode = "400", description = "Scope mismatch or invalid input"),
            @ApiResponse(responseCode = "404", description = "Settlement run or vendor invoice not found")
    })
    public ResponseEntity<ReconciliationResponse> reconcile(
            @Valid @RequestBody ReconciliationRequest request,
            Authentication authentication
    ) {
        String reconciledBy = authentication != null && authentication.getName() != null 
                ? authentication.getName() 
                : "SYSTEM_RECONCILER";
        log.info("Triggering reconciliation between run {} and invoice {} by {}", 
                request.getSettlementRunId(), request.getVendorInvoiceId(), reconciledBy);
        return ResponseEntity.ok(reconciliationService.reconcile(request, reconciledBy));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Get Reconciliation by ID", description = "Retrieves reconciliation record and discrepancy details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconciliation found"),
            @ApiResponse(responseCode = "404", description = "Reconciliation not found")
    })
    public ResponseEntity<ReconciliationResponse> getReconciliationById(@PathVariable UUID id) {
        return ResponseEntity.ok(reconciliationService.getReconciliationById(id));
    }
}
