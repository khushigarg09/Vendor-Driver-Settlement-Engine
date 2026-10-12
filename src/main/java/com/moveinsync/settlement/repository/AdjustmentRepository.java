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

    @org.springframework.data.jpa.repository.Query("""
        SELECT a FROM Adjustment a
        JOIN a.settlement s
        WHERE s.driver.id = :driverId
          AND a.status = :status
          AND a.consumedRun IS NULL
        ORDER BY a.createdAt ASC
    """)
    List<Adjustment> findPendingAdjustmentsForDriver(
            @org.springframework.data.repository.query.Param("driverId") UUID driverId,
            @org.springframework.data.repository.query.Param("status") com.moveinsync.settlement.entity.enums.AdjustmentStatus status
    );
}
