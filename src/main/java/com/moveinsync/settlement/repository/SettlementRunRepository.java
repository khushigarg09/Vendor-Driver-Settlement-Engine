package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.SettlementRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRunRepository extends JpaRepository<SettlementRun, UUID> {

    Optional<SettlementRun> findByVendorIdAndBusinessUnitIdAndBillingPeriod(
            UUID vendorId, UUID businessUnitId, String billingPeriod
    );

    @Query("""
        SELECT sr FROM SettlementRun sr
        LEFT JOIN FETCH sr.settlements s
        WHERE sr.id = :id
    """)
    Optional<SettlementRun> findByIdWithSettlements(@Param("id") UUID id);

    List<SettlementRun> findAllByOrderByCreatedAtDesc();
}
