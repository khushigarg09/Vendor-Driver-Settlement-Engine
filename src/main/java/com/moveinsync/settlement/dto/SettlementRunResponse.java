package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary response for a settlement run")
public class SettlementRunResponse {

    @Schema(description = "Settlement Run ID")
    private UUID id;

    @Schema(description = "Vendor ID")
    private UUID vendorId;

    @Schema(description = "Business Unit ID")
    private UUID businessUnitId;

    @Schema(description = "Billing Period (YYYY-MM)", example = "2026-10")
    private String billingPeriod;

    @Schema(description = "Run execution cycle number", example = "1")
    private Integer runNumber;

    @Schema(description = "Current lifecycle status")
    private SettlementRunStatus status;

    @Schema(description = "Total number of drivers included in this run", example = "2")
    private Integer driversSettledCount;

    @Schema(description = "Total gross base pay for all drivers", example = "47700.00")
    private BigDecimal totalGrossBasePay;

    @Schema(description = "Total incentives across all drivers", example = "3000.00")
    private BigDecimal totalIncentives;

    @Schema(description = "Total penalties deducted across all drivers", example = "700.00")
    private BigDecimal totalPenalties;

    @Schema(description = "Total Section 194C TDS tax withheld", example = "1035.00")
    private BigDecimal totalTdsWithheld;

    @Schema(description = "Total net payout amount", example = "48965.00")
    private BigDecimal totalNetPayout;

    @Schema(description = "True if this response returned an existing finalized run (idempotent replay)", example = "false")
    @com.fasterxml.jackson.annotation.JsonProperty("idempotentReplay")
    private boolean idempotentReplay;

    @Schema(description = "Execution timestamp")
    private Instant createdAt;

    @Schema(description = "User or system agent that initiated the run")
    private String createdBy;
}
