package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Payload to register a dispute against a finalized driver settlement")
public class DisputeRequest {

    @NotNull(message = "Settlement ID is mandatory")
    @Schema(description = "Target Settlement ID", example = "a1c5d983-2947-49d7-814c-11e2f41e976e")
    private UUID settlementId;

    @Schema(description = "Disputed trip ID (if disputing a trip fare)", example = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private UUID disputedTripId;

    @Schema(description = "Disputed violation ID (if disputing a penalty deduction)", example = "cccccccc-cccc-cccc-cccc-cccccccccccc")
    private UUID disputedViolationId;

    @NotBlank(message = "Reason for dispute is mandatory")
    @Schema(description = "Factual justification for dispute", example = "Mid-month rate update on Oct 15th was not applied.")
    private String reason;

    @NotNull(message = "Claimed delta amount is mandatory")
    @DecimalMin(value = "0.01", message = "Claimed delta must be greater than zero")
    @Schema(description = "Claimed delta adjustment amount", example = "30.00")
    private BigDecimal claimedDelta;
}
