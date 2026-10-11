package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.PayslipResponse;
import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.SettlementRunResponse;

import java.util.List;
import java.util.UUID;

public interface SettlementRunService {

    /**
     * Executes or idempotently replays a settlement run for a vendor, business unit, and billing period.
     */
    SettlementRunResponse executeSettlementRun(SettlementRunRequest request, String createdBy);

    /**
     * Retrieves summary information for a settlement run by its unique identifier.
     */
    SettlementRunResponse getSettlementRunById(UUID id);

    /**
     * Lists all recorded settlement runs ordered by creation date descending.
     */
    List<SettlementRunResponse> listSettlementRuns();

    /**
     * Lists recorded settlement runs with pagination ordered by creation date descending.
     */
    List<SettlementRunResponse> listSettlementRuns(int page, int size);

    /**
     * Approves and finalizes a settlement run and its constituent driver settlements.
     */
    SettlementRunResponse approveSettlementRun(UUID id, String approvedBy);

    /**
     * Retrieves an itemized payslip for a driver in a specific billing period.
     */
    PayslipResponse getDriverPayslip(UUID driverId, String billingPeriod);
}
