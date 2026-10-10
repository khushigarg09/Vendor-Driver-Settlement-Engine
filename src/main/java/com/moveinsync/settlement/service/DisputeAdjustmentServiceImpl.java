package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.*;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.entity.enums.AdjustmentStatus;
import com.moveinsync.settlement.entity.enums.AdjustmentType;
import com.moveinsync.settlement.entity.enums.DisputeStatus;
import com.moveinsync.settlement.entity.enums.SettlementStatus;
import com.moveinsync.settlement.entity.enums.TripSettlementStatus;
import com.moveinsync.settlement.exception.ConflictException;
import com.moveinsync.settlement.exception.ResourceNotFoundException;
import com.moveinsync.settlement.exception.SettlementDomainException;
import com.moveinsync.settlement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeAdjustmentServiceImpl implements DisputeAdjustmentService {

    private final DisputeRepository disputeRepository;
    private final AdjustmentRepository adjustmentRepository;
    private final SettlementRepository settlementRepository;
    private final TripRepository tripRepository;
    private final ViolationRepository violationRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public DisputeResponse fileDispute(DisputeRequest request, String filedBy) {
        log.info("Filing dispute on settlement {} by {}: reason='{}', claimedDelta=₹{}",
                request.getSettlementId(), filedBy, request.getReason(), request.getClaimedDelta());

        Settlement settlement = settlementRepository.findById(request.getSettlementId())
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found: " + request.getSettlementId()));

        if (settlement.getSettlementRun() != null && settlement.getSettlementRun().getStatus() == com.moveinsync.settlement.entity.enums.SettlementRunStatus.STARTED) {
            throw new SettlementDomainException("Cannot file dispute against a settlement whose run is in STARTED status. Settlement must be CALCULATED or FINALIZED.");
        }

        if (request.getDisputedTripId() == null && request.getDisputedViolationId() == null) {
            throw new SettlementDomainException("Dispute must target either a specific trip or a violation.");
        }

        Trip trip = null;
        if (request.getDisputedTripId() != null) {
            trip = tripRepository.findById(request.getDisputedTripId())
                    .orElseThrow(() -> new ResourceNotFoundException("Disputed trip not found: " + request.getDisputedTripId()));

            Optional<Dispute> existingDispute = disputeRepository
                    .findBySettlementIdAndTripIdAndStatus(settlement.getId(), trip.getId(), DisputeStatus.OPEN);
            if (existingDispute.isPresent()) {
                throw new ConflictException("An active dispute is already open for trip " + trip.getId());
            }

            trip.setSettlementStatus(TripSettlementStatus.DISPUTED);
            tripRepository.save(trip);
        }

        Violation violation = null;
        if (request.getDisputedViolationId() != null) {
            violation = violationRepository.findById(request.getDisputedViolationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Disputed violation not found: " + request.getDisputedViolationId()));

            Optional<Dispute> existingDispute = disputeRepository
                    .findBySettlementIdAndViolationIdAndStatus(settlement.getId(), violation.getId(), DisputeStatus.OPEN);
            if (existingDispute.isPresent()) {
                throw new ConflictException("An active dispute is already open for violation " + violation.getId());
            }
        }

        Dispute dispute = Dispute.builder()
                .id(UUID.randomUUID())
                .settlement(settlement)
                .trip(trip)
                .violation(violation)
                .reason(request.getReason())
                .claimedDelta(request.getClaimedDelta())
                .status(DisputeStatus.OPEN)
                .createdAt(Instant.now())
                .build();

        Dispute saved = disputeRepository.save(dispute);
        log.info("Dispute {} successfully registered in OPEN state", saved.getId());

        auditService.recordEvent(
                "DISPUTE_FILED",
                "DISPUTE",
                saved.getId().toString(),
                filedBy,
                "ROLE_EMPLOYEE",
                Map.of(
                        "settlementId", settlement.getId().toString(),
                        "reason", request.getReason(),
                        "claimedDelta", request.getClaimedDelta()
                )
        );

        return mapToDisputeResponse(saved);
    }

    @Override
    @Transactional
    public DisputeResolutionResponse approveDispute(UUID disputeId, DisputeResolutionRequest request, String approvedBy) {
        log.info("Approving dispute {} by {}", disputeId, approvedBy);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));

        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new SettlementDomainException(String.format(
                    "Dispute %s cannot be approved because it is in %s state.", disputeId, dispute.getStatus()
            ));
        }

        BigDecimal delta = (request != null && request.getApprovedDelta() != null)
                ? request.getApprovedDelta()
                : dispute.getClaimedDelta();

        AdjustmentType adjType = dispute.getTrip() != null
                ? AdjustmentType.TRIP_RATE_CORRECTION
                : (dispute.getViolation() != null ? AdjustmentType.PENALTY_WAIVER : AdjustmentType.TRIP_RATE_CORRECTION);

        // Core Financial Invariant: Original Settlement is NOT modified!
        // We instantiate an independent immutable Adjustment and signed AdjustmentLine.
        Adjustment adjustment = Adjustment.builder()
                .id(UUID.randomUUID())
                .dispute(dispute)
                .settlement(dispute.getSettlement())
                .adjustmentType(adjType)
                .deltaAmount(delta)
                .status(AdjustmentStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        String lineDesc = (request != null && request.getNotes() != null && !request.getNotes().isBlank())
                ? request.getNotes()
                : "Approved delta adjustment: " + dispute.getReason();

        AdjustmentLine line = AdjustmentLine.builder()
                .id(UUID.randomUUID())
                .adjustment(adjustment)
                .signedAmount(delta)
                .description(lineDesc)
                .createdAt(Instant.now())
                .build();

        adjustment.addLine(line);
        Adjustment savedAdj = adjustmentRepository.save(adjustment);

        // Transition dispute state to APPROVED
        dispute.setStatus(DisputeStatus.APPROVED);
        dispute.setResolvedBy(approvedBy != null ? approvedBy : "APPROVER");
        dispute.setResolutionNotes(request != null ? request.getNotes() : null);
        disputeRepository.save(dispute);

        // If trip was disputed, transition settlement status back to SETTLED
        if (dispute.getTrip() != null) {
            Trip trip = dispute.getTrip();
            trip.setSettlementStatus(TripSettlementStatus.SETTLED);
            tripRepository.save(trip);
        }

        log.info("Dispute {} approved with Adjustment {} of ₹{}", dispute.getId(), savedAdj.getId(), delta);

        auditService.recordEvent(
                "DISPUTE_APPROVED",
                "DISPUTE",
                dispute.getId().toString(),
                approvedBy,
                "ROLE_APPROVER",
                Map.of(
                        "settlementId", dispute.getSettlement().getId().toString(),
                        "adjustmentId", savedAdj.getId().toString(),
                        "approvedDelta", delta
                )
        );

        return DisputeResolutionResponse.builder()
                .disputeId(dispute.getId())
                .status(DisputeStatus.APPROVED)
                .adjustment(mapToAdjustmentResponse(savedAdj))
                .build();
    }

    @Override
    @Transactional
    public DisputeResponse rejectDispute(UUID disputeId, DisputeResolutionRequest request, String rejectedBy) {
        log.info("Rejecting dispute {} by {}", disputeId, rejectedBy);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));

        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new SettlementDomainException(String.format(
                    "Dispute %s cannot be rejected because it is in %s state.", disputeId, dispute.getStatus()
            ));
        }

        String notes = (request != null && request.getNotes() != null && !request.getNotes().isBlank())
                ? request.getNotes()
                : "Dispute rejected after review.";

        dispute.setStatus(DisputeStatus.REJECTED);
        dispute.setResolvedBy(rejectedBy != null ? rejectedBy : "APPROVER");
        dispute.setResolutionNotes(notes);
        Dispute saved = disputeRepository.save(dispute);

        // Reset trip status if it was disputed
        if (dispute.getTrip() != null) {
            Trip trip = dispute.getTrip();
            trip.setSettlementStatus(TripSettlementStatus.SETTLED);
            tripRepository.save(trip);
        }

        log.info("Dispute {} rejected by {}", dispute.getId(), rejectedBy);

        auditService.recordEvent(
                "DISPUTE_REJECTED",
                "DISPUTE",
                saved.getId().toString(),
                rejectedBy,
                "ROLE_APPROVER",
                Map.of(
                        "settlementId", dispute.getSettlement().getId().toString(),
                        "notes", notes
                )
        );

        return mapToDisputeResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse getDisputeById(UUID disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));
        return mapToDisputeResponse(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeResponse> listDisputes(UUID settlementId) {
        List<Dispute> disputes = (settlementId != null)
                ? disputeRepository.findBySettlementId(settlementId)
                : disputeRepository.findAllByOrderByCreatedAtDesc();
        return disputes.stream().map(this::mapToDisputeResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdjustmentResponse> listAdjustments(UUID settlementId) {
        List<Adjustment> list = (settlementId != null)
                ? adjustmentRepository.findBySettlementId(settlementId)
                : adjustmentRepository.findAllByOrderByCreatedAtDesc();
        return list.stream().map(this::mapToAdjustmentResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdjustmentResponse getAdjustmentById(UUID adjustmentId) {
        Adjustment adj = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Adjustment record not found: " + adjustmentId));
        return mapToAdjustmentResponse(adj);
    }

    private DisputeResponse mapToDisputeResponse(Dispute d) {
        return DisputeResponse.builder()
                .id(d.getId())
                .settlementId(d.getSettlement().getId())
                .tripId(d.getTrip() != null ? d.getTrip().getId() : null)
                .violationId(d.getViolation() != null ? d.getViolation().getId() : null)
                .reason(d.getReason())
                .claimedDelta(d.getClaimedDelta())
                .status(d.getStatus())
                .resolvedBy(d.getResolvedBy())
                .resolutionNotes(d.getResolutionNotes())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private AdjustmentResponse mapToAdjustmentResponse(Adjustment a) {
        List<AdjustmentLineResponse> lines = a.getLines().stream()
                .map(l -> AdjustmentLineResponse.builder()
                        .id(l.getId())
                        .signedAmount(l.getSignedAmount())
                        .description(l.getDescription())
                        .build())
                .toList();

        return AdjustmentResponse.builder()
                .id(a.getId())
                .disputeId(a.getDispute().getId())
                .settlementId(a.getSettlement().getId())
                .adjustmentType(a.getAdjustmentType())
                .deltaAmount(a.getDeltaAmount())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .lines(lines)
                .build();
    }
}
