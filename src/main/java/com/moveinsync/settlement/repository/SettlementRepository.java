package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Optional<Settlement> findBySettlementRunIdAndDriverId(UUID settlementRunId, UUID driverId);

    @Query("""
        SELECT s FROM Settlement s
        LEFT JOIN FETCH s.lines
        WHERE s.driver.id = :driverId
          AND s.billingPeriod = :billingPeriod
    """)
    Optional<Settlement> findByDriverIdAndBillingPeriodWithLines(
            @Param("driverId") UUID driverId,
            @Param("billingPeriod") String billingPeriod
    );

    List<Settlement> findBySettlementRunId(UUID settlementRunId);
}
