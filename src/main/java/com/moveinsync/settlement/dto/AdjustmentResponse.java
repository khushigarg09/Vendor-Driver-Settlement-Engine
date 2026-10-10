package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.enums.AdjustmentStatus;
import com.moveinsync.settlement.entity.enums.AdjustmentType;
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
@Schema(description = "Response containing delta adjustment details")
public class AdjustmentResponse {

    @Schema(description = "Adjustment record identifier")
    private UUID id;

    @Schema(description = "Originating dispute identifier")
    private UUID disputeId;

    @Schema(description = "Associated settlement identifier")
    private UUID settlementId;

    @Schema(description = "Type of adjustment applied")
    private AdjustmentType adjustmentType;

    @Schema(description = "Net delta adjustment amount", example = "30.00")
    private BigDecimal deltaAmount;

    @Schema(description = "Current payout status of adjustment")
    private AdjustmentStatus status;

    @Schema(description = "Timestamp when adjustment was generated")
    private Instant createdAt;

    @Schema(description = "Itemized adjustment line items")
    private List<AdjustmentLineResponse> lines;
}
