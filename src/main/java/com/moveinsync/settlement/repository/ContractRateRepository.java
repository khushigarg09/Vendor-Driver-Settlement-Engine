package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.ContractRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRateRepository extends JpaRepository<ContractRate, UUID> {

    @Query("""
        SELECT cr FROM ContractRate cr
        WHERE cr.contract.id = :contractId
          AND cr.vehicleCategory = :vehicleCategory
          AND cr.effectiveFrom <= :timestamp
          AND (cr.effectiveTo IS NULL OR cr.effectiveTo > :timestamp)
        ORDER BY cr.effectiveFrom DESC
    """)
    Optional<ContractRate> findApplicableRate(
            @Param("contractId") UUID contractId,
            @Param("vehicleCategory") String vehicleCategory,
            @Param("timestamp") Instant timestamp
    );

    List<ContractRate> findByContractId(UUID contractId);
}
