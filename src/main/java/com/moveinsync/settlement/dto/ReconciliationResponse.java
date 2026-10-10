package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.enums.ReconciliationStatus;
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
@Schema(description = "Response containing invoice reconciliation outcome and variance details")
public class ReconciliationResponse {

    @Schema(description = "Reconciliation record ID")
    private UUID reconciliationId;

    @Schema(description = "Settlement Run ID")
    private UUID settlementRunId;

    @Schema(description = "Vendor Invoice ID")
    private UUID vendorInvoiceId;

    @Schema(description = "System calculated net payout amount", example = "2530.00")
    private BigDecimal systemAmount;

    @Schema(description = "Vendor claimed invoice amount", example = "2580.00")
    private BigDecimal invoiceAmount;

    @Schema(description = "Variance (Claimed - System)", example = "50.00")
    private BigDecimal varianceAmount;

    @Schema(description = "Permissible tolerance applied", example = "100.00")
    private BigDecimal toleranceApplied;

    @Schema(description = "Matching classification status")
    private ReconciliationStatus status;

    @Schema(description = "Auditable human-readable variance explanation")
    private String explanation;

    @Schema(description = "Timestamp when reconciliation was performed")
    private Instant reconciledAt;

    @Schema(description = "User or system agent who initiated reconciliation")
    private String reconciledBy;
}
