package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Adjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdjustmentRepository extends JpaRepository<Adjustment, UUID> {
    List<Adjustment> findByDisputeId(UUID disputeId);
    List<Adjustment> findBySettlementId(UUID settlementId);
    List<Adjustment> findAllByOrderByCreatedAtDesc();
}
