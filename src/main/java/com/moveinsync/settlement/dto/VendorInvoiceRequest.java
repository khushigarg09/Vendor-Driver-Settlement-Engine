package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to submit an external vendor invoice for a billing cycle")
public class VendorInvoiceRequest {

    @NotNull(message = "vendorId is required")
    @Schema(description = "Unique UUID of the Vendor", example = "11111111-1111-1111-1111-111111111111")
    private UUID vendorId;

    @NotNull(message = "businessUnitId is required")
    @Schema(description = "Unique UUID of the Business Unit", example = "22222222-2222-2222-2222-222222222222")
    private UUID businessUnitId;

    @NotBlank(message = "invoiceNumber is required")
    @Schema(description = "Vendor's external tax invoice number", example = "INV-2026-OCT-099")
    private String invoiceNumber;

    @NotBlank(message = "billingPeriod is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "billingPeriod must be in YYYY-MM format")
    @Schema(description = "Billing Period in YYYY-MM format", example = "2026-10")
    private String billingPeriod;

    @NotNull(message = "claimedAmount is required")
    @DecimalMin(value = "0.01", message = "claimedAmount must be greater than zero")
    @Schema(description = "Total claimed amount on the invoice", example = "2580.00")
    private BigDecimal claimedAmount;

    @NotNull(message = "invoiceDate is required")
    @Schema(description = "Invoice issuance date", example = "2026-11-02")
    private LocalDate invoiceDate;
}
