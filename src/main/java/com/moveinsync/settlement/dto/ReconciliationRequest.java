package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to trigger automated reconciliation between a settlement run and a vendor invoice")
public class ReconciliationRequest {

    @NotNull(message = "settlementRunId is required")
    @Schema(description = "UUID of the finalized settlement run")
    private UUID settlementRunId;

    @NotNull(message = "vendorInvoiceId is required")
    @Schema(description = "UUID of the submitted vendor invoice")
    private UUID vendorInvoiceId;

    @DecimalMin(value = "0.00", message = "tolerance cannot be negative")
    @Schema(description = "Permissible variance threshold in INR (default ₹100.00)", example = "100.00")
    private BigDecimal tolerance;
}
