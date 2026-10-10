package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.enums.SettlementLineType;
import com.moveinsync.settlement.entity.enums.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Itemized payslip with signed lines verifying exact net payout")
public class PayslipResponse {

    @Schema(description = "Settlement ID")
    private UUID settlementId;

    @Schema(description = "Settlement Run ID")
    private UUID settlementRunId;

    @Schema(description = "Driver ID")
    private UUID driverId;

    @Schema(description = "Driver Code", example = "DRV-10042")
    private String driverCode;

    @Schema(description = "Driver full name", example = "Rajesh Kumar")
    private String driverName;

    @Schema(description = "Billing Period (YYYY-MM)", example = "2026-10")
    private String billingPeriod;

    @Schema(description = "Settlement Lifecycle Status")
    private SettlementStatus status;

    @Schema(description = "Number of completed eligible trips included", example = "180")
    private Integer tripsCount;

    @Schema(description = "Financial breakdown summary")
    private PayslipSummary summary;

    @Schema(description = "Itemized signed ledger lines")
    private List<PayslipLineItemResponse> lineItems;

    @Schema(description = "Paisa-level mathematical reconciliation check")
    private PayslipVerification verification;

    @Schema(description = "Timestamp when payslip was generated")
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayslipSummary {
        private BigDecimal grossBasePay;
        private BigDecimal incentivesTotal;
        private BigDecimal penaltiesTotal;
        private BigDecimal tdsTaxWithheld;
        private BigDecimal netPayout;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayslipLineItemResponse {
        private UUID id;
        private SettlementLineType lineType;
        private String description;
        private String sourceReferenceId;
        private BigDecimal signedAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayslipVerification {
        private BigDecimal linesSum;
        private BigDecimal netPayout;
        @com.fasterxml.jackson.annotation.JsonProperty("isReconciled")
        private boolean isReconciled;
    }
}
