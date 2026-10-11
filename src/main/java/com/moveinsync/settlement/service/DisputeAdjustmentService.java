package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.AdjustmentResponse;
import com.moveinsync.settlement.dto.DisputeRequest;
import com.moveinsync.settlement.dto.DisputeResolutionRequest;
import com.moveinsync.settlement.dto.DisputeResolutionResponse;
import com.moveinsync.settlement.dto.DisputeResponse;

import java.util.List;
import java.util.UUID;

public interface DisputeAdjustmentService {

    /**
     * Files a new dispute against an eligible trip or violation within a finalized settlement.
     */
    DisputeResponse fileDispute(DisputeRequest request, String filedBy);

    /**
     * Approves a dispute, calculating the isolated delta and generating an immutable Adjustment record.
     */
    DisputeResolutionResponse approveDispute(UUID disputeId, DisputeResolutionRequest request, String approvedBy);

    /**
     * Rejects an open dispute without creating adjustment records.
     */
    DisputeResponse rejectDispute(UUID disputeId, DisputeResolutionRequest request, String rejectedBy);

    /**
     * Retrieves a dispute by its unique identifier.
     */
    DisputeResponse getDisputeById(UUID disputeId);

    /**
     * Lists disputes, optionally filtered by target settlement ID.
     */
    List<DisputeResponse> listDisputes(UUID settlementId);

    /**
     * Lists disputes with pagination, optionally filtered by target settlement ID.
     */
    List<DisputeResponse> listDisputes(UUID settlementId, int page, int size);

    /**
     * Lists adjustments, optionally filtered by target settlement ID.
     */
    List<AdjustmentResponse> listAdjustments(UUID settlementId);

    /**
     * Retrieves an adjustment record by its unique identifier.
     */
    AdjustmentResponse getAdjustmentById(UUID adjustmentId);
}
