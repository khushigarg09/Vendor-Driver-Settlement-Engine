package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.enums.DisputeStatus;
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
@Schema(description = "Response containing dispute details and status")
public class DisputeResponse {

    @Schema(description = "Dispute identifier")
    private UUID id;

    @Schema(description = "Referenced Settlement ID")
    private UUID settlementId;

    @Schema(description = "Disputed Trip ID (if applicable)")
    private UUID tripId;

    @Schema(description = "Disputed Violation ID (if applicable)")
    private UUID violationId;

    @Schema(description = "Justification reason")
    private String reason;

    @Schema(description = "Claimed delta adjustment amount", example = "30.00")
    private BigDecimal claimedDelta;

    @Schema(description = "Current lifecycle status of dispute")
    private DisputeStatus status;

    @Schema(description = "User or approver who resolved the dispute")
    private String resolvedBy;

    @Schema(description = "Resolution commentary or notes")
    private String resolutionNotes;

    @Schema(description = "Timestamp when dispute was raised")
    private Instant createdAt;
}
