package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.AuditLogResponse;
import com.moveinsync.settlement.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Endpoints for inspecting auditable financial actions and lifecycle events")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List All Audit Logs",
               description = "Retrieves all recorded audit events across settlements, invoices, reconciliations, and disputes. Restricted to ADMIN with pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    public ResponseEntity<List<AuditLogResponse>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(auditService.getAllAuditLogs(page, size));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    @Operation(summary = "Get Audit Logs for Specific Entity",
               description = "Retrieves audit history for a given entity type and ID (e.g. SETTLEMENT_RUN, DISPUTE, VENDOR_INVOICE). Accessible to ADMIN and APPROVER with pagination.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entity audit logs retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN or APPROVER role")
    })
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(auditService.getAuditLogsByEntity(entityType, entityId, page, size));
    }
}
