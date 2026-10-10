package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to trigger or replay a monthly settlement run")
public class SettlementRunRequest {

    @NotNull(message = "vendorId is required")
    @Schema(description = "Unique UUID of the Vendor", example = "11111111-1111-1111-1111-111111111111")
    private UUID vendorId;

    @NotNull(message = "businessUnitId is required")
    @Schema(description = "Unique UUID of the Business Unit", example = "22222222-2222-2222-2222-222222222222")
    private UUID businessUnitId;

    @NotBlank(message = "billingPeriod is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "billingPeriod must be in YYYY-MM format (e.g., 2026-10)")
    @Schema(description = "Billing Period in YYYY-MM format", example = "2026-10")
    private String billingPeriod;
}
