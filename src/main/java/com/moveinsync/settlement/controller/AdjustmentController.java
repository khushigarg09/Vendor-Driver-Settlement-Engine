package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.AdjustmentResponse;
import com.moveinsync.settlement.service.DisputeAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/adjustments")
@RequiredArgsConstructor
@Tag(name = "Adjustments", description = "Endpoints for immutable delta adjustment records and line items")
public class AdjustmentController {

    private final DisputeAdjustmentService disputeAdjustmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'APPROVER')")
    @Operation(summary = "List Adjustments", description = "Retrieves delta adjustments, optionally filtered by settlement ID.")
    public ResponseEntity<List<AdjustmentResponse>> listAdjustments(@RequestParam(required = false) UUID settlementId) {
        return ResponseEntity.ok(disputeAdjustmentService.listAdjustments(settlementId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'APPROVER')")
    @Operation(summary = "Get Adjustment by ID", description = "Retrieves details and signed line items of a specific adjustment.")
    public ResponseEntity<AdjustmentResponse> getAdjustmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(disputeAdjustmentService.getAdjustmentById(id));
    }
}
