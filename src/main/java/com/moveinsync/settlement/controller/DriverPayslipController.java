package com.moveinsync.settlement.controller;

import com.moveinsync.settlement.dto.PayslipResponse;
import com.moveinsync.settlement.service.SettlementRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver Payslips", description = "Endpoints for retrieving itemized driver payslips and signed financial lines")
public class DriverPayslipController {

    private final SettlementRunService settlementRunService;

    @GetMapping("/{driverId}/payslips/{billingPeriod}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER') or (hasRole('EMPLOYEE') and @settlementSecurity.canAccessDriver(authentication, #driverId))")
    @Operation(summary = "Get Driver Payslip",
               description = "Retrieves itemized driver payslip containing signed lines, tax deductions, and net payout with zero-drift reconciliation check.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Driver payslip found"),
            @ApiResponse(responseCode = "404", description = "Payslip not found for the driver and period")
    })
    public ResponseEntity<PayslipResponse> getDriverPayslip(
            @PathVariable UUID driverId,
            @PathVariable String billingPeriod
    ) {
        return ResponseEntity.ok(settlementRunService.getDriverPayslip(driverId, billingPeriod));
    }
}
