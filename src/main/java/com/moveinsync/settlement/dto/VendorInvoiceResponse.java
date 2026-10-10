package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing submitted vendor invoice details")
public class VendorInvoiceResponse {

    @Schema(description = "Unique Invoice ID")
    private UUID id;

    @Schema(description = "Vendor ID")
    private UUID vendorId;

    @Schema(description = "Business Unit ID")
    private UUID businessUnitId;

    @Schema(description = "Invoice Number")
    private String invoiceNumber;

    @Schema(description = "Billing Period (YYYY-MM)")
    private String billingPeriod;

    @Schema(description = "Claimed Invoice Amount", example = "2580.00")
    private BigDecimal claimedAmount;

    @Schema(description = "Invoice Issuance Date")
    private LocalDate invoiceDate;

    @Schema(description = "Processing status", example = "SUBMITTED")
    private String status;

    @Schema(description = "Submission timestamp")
    private Instant createdAt;
}
