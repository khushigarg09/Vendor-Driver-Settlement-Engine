package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to resolve (approve or reject) a dispute")
public class DisputeResolutionRequest {

    @DecimalMin(value = "0.01", message = "Approved delta must be greater than zero")
    @Schema(description = "Approved delta amount (defaults to claimedDelta if not specified)", example = "30.00")
    private BigDecimal approvedDelta;

    @Schema(description = "Approver justification commentary or audit notes", example = "Verified rate card; approved ₹30.00 rate differential.")
    private String notes;
}
