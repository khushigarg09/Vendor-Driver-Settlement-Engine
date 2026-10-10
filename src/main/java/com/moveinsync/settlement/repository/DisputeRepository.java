package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Dispute;
import com.moveinsync.settlement.entity.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    List<Dispute> findBySettlementId(UUID settlementId);

    Optional<Dispute> findBySettlementIdAndTripIdAndStatus(UUID settlementId, UUID tripId, DisputeStatus status);

    Optional<Dispute> findBySettlementIdAndViolationIdAndStatus(UUID settlementId, UUID violationId, DisputeStatus status);

    List<Dispute> findAllByOrderByCreatedAtDesc();
}
