package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.enums.DisputeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response returned upon resolving a dispute")
public class DisputeResolutionResponse {

    @Schema(description = "Dispute ID")
    private UUID disputeId;

    @Schema(description = "Updated dispute status (APPROVED or REJECTED)")
    private DisputeStatus status;

    @Schema(description = "Generated delta adjustment record (present only if APPROVED)")
    private AdjustmentResponse adjustment;
}
