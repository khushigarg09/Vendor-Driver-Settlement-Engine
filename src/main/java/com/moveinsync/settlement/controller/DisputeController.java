package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.DisputeRequest;
import com.moveinsync.settlement.dto.DisputeResolutionRequest;
import com.moveinsync.settlement.dto.DisputeResolutionResponse;
import com.moveinsync.settlement.dto.DisputeResponse;
import com.moveinsync.settlement.service.DisputeAdjustmentService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
@Tag(name = "Disputes", description = "Endpoints for driver/vendor settlement dispute filing, review, and resolution")
public class DisputeController {

    private final DisputeAdjustmentService disputeAdjustmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'APPROVER')")
    @Operation(summary = "File Settlement Dispute", description = "Raises an auditable dispute against a trip or penalty deduction in a finalized settlement.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispute filed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or unfinalized settlement"),
            @ApiResponse(responseCode = "404", description = "Settlement, trip, or violation not found"),
            @ApiResponse(responseCode = "409", description = "Active dispute already exists for this entity")
    })
    public ResponseEntity<DisputeResponse> fileDispute(
            @Valid @RequestBody DisputeRequest request,
            Authentication authentication
    ) {
        String filedBy = authentication != null && authentication.getName() != null 
                ? authentication.getName() 
                : "USER";
        log.info("Filing dispute by {}", filedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(disputeAdjustmentService.fileDispute(request, filedBy));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Approve Dispute", description = "Approves a dispute, generates an isolated immutable adjustment record, and leaves the original settlement intact.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute approved and delta adjustment generated"),
            @ApiResponse(responseCode = "400", description = "Dispute not in OPEN status"),
            @ApiResponse(responseCode = "404", description = "Dispute not found")
    })
    public ResponseEntity<DisputeResolutionResponse> approveDispute(
            @PathVariable UUID id,
            @RequestBody(required = false) DisputeResolutionRequest request,
            Authentication authentication
    ) {
        String approvedBy = authentication != null && authentication.getName() != null 
                ? authentication.getName() 
                : "APPROVER";
        log.info("Approving dispute {} by {}", id, approvedBy);
        return ResponseEntity.ok(disputeAdjustmentService.approveDispute(id, request, approvedBy));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Reject Dispute", description = "Rejects an open dispute after audit review without generating adjustment records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute rejected"),
            @ApiResponse(responseCode = "400", description = "Dispute not in OPEN status"),
            @ApiResponse(responseCode = "404", description = "Dispute not found")
    })
    public ResponseEntity<DisputeResponse> rejectDispute(
            @PathVariable UUID id,
            @RequestBody(required = false) DisputeResolutionRequest request,
            Authentication authentication
    ) {
        String rejectedBy = authentication != null && authentication.getName() != null 
                ? authentication.getName() 
                : "APPROVER";
        log.info("Rejecting dispute {} by {}", id, rejectedBy);
        return ResponseEntity.ok(disputeAdjustmentService.rejectDispute(id, request, rejectedBy));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'APPROVER')")
    @Operation(summary = "Get Dispute by ID", description = "Retrieves details of a specific dispute.")
    public ResponseEntity<DisputeResponse> getDisputeById(@PathVariable UUID id) {
        return ResponseEntity.ok(disputeAdjustmentService.getDisputeById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN', 'APPROVER')")
    @Operation(summary = "List Disputes", description = "Retrieves all disputes, optionally filtered by settlement ID.")
    public ResponseEntity<List<DisputeResponse>> listDisputes(@RequestParam(required = false) UUID settlementId) {
        return ResponseEntity.ok(disputeAdjustmentService.listDisputes(settlementId));
    }
}
