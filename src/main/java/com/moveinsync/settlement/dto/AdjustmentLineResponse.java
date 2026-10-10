package com.moveinsync.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Itemized adjustment line item")
public class AdjustmentLineResponse {

    @Schema(description = "Adjustment line ID")
    private UUID id;

    @Schema(description = "Signed delta amount (+credit / -debit)", example = "30.00")
    private BigDecimal signedAmount;

    @Schema(description = "Line description", example = "Dispute rate correction: trip 12345")
    private String description;
}
