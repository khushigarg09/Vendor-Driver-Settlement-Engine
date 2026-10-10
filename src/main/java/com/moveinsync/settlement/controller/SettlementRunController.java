package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.SettlementRunResponse;
import com.moveinsync.settlement.service.SettlementRunService;
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
@RequestMapping("/api/v1/settlement-runs")
@RequiredArgsConstructor
@Tag(name = "Settlement Runs", description = "Endpoints for initiating, retrieving, and approving monthly settlement runs")
public class SettlementRunController {

    private final SettlementRunService settlementRunService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Execute or Idempotently Replay Settlement Run",
               description = "Calculates monthly settlements for all drivers under the specified vendor and business unit. Replaying the same scope returns existing results.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Settlement run successfully calculated"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay: existing settlement run returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request format or billing period"),
            @ApiResponse(responseCode = "404", description = "Vendor or Business Unit not found"),
            @ApiResponse(responseCode = "409", description = "Concurrent execution conflict")
    })
    public ResponseEntity<SettlementRunResponse> executeSettlementRun(
            @Valid @RequestBody SettlementRunRequest request,
            Authentication authentication
    ) {
        String user = (authentication != null) ? authentication.getName() : "ANONYMOUS";
        SettlementRunResponse response = settlementRunService.executeSettlementRun(request, user);

        HttpStatus status = response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "List Settlement Runs", description = "Returns all historical settlement runs ordered by creation date descending.")
    public ResponseEntity<List<SettlementRunResponse>> listSettlementRuns() {
        return ResponseEntity.ok(settlementRunService.listSettlementRuns());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Get Settlement Run by ID", description = "Retrieves complete financial summary for a specific settlement run.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settlement run found"),
            @ApiResponse(responseCode = "404", description = "Settlement run not found")
    })
    public ResponseEntity<SettlementRunResponse> getSettlementRunById(@PathVariable UUID id) {
        return ResponseEntity.ok(settlementRunService.getSettlementRunById(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Approve Settlement Run", description = "Transitions settlement run status to FINALIZED and constituent driver settlements to APPROVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settlement run approved"),
            @ApiResponse(responseCode = "404", description = "Settlement run not found")
    })
    public ResponseEntity<SettlementRunResponse> approveSettlementRun(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String user = (authentication != null) ? authentication.getName() : "ANONYMOUS";
        return ResponseEntity.ok(settlementRunService.approveSettlementRun(id, user));
    }
}
