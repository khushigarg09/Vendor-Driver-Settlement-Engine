package com.moveinsync.settlement.service;

import com.moveinsync.settlement.entity.BusinessUnit;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementRunClaimService {

    private final SettlementRunRepository settlementRunRepository;

    /**
     * Atomically claims a settlement run upfront in a separate transaction.
     * If a previous attempt FAILED, it reuses the run record and increments the runNumber/attempt count.
     * If an abandoned claim has been STARTED for > 15 minutes, it recovers the claim.
     * Throws DataIntegrityViolationException or ConflictException if an active claim exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementRun claimRun(Vendor vendor, BusinessUnit businessUnit, String billingPeriod, String createdBy) {
        log.info("Durable run claim initiated: vendor={}, bu={}, period={}, createdBy={}",
                vendor.getId(), businessUnit.getId(), billingPeriod, createdBy);

        java.util.Optional<SettlementRun> existingOpt = settlementRunRepository
                .findByVendorIdAndBusinessUnitIdAndBillingPeriod(vendor.getId(), businessUnit.getId(), billingPeriod);

        if (existingOpt.isPresent()) {
            SettlementRun existing = existingOpt.get();
            if (existing.getStatus() == SettlementRunStatus.FAILED) {
                log.info("Reusing existing FAILED settlement run {} for retry (incrementing runNumber from {})",
                        existing.getId(), existing.getRunNumber());
                existing.setStatus(SettlementRunStatus.STARTED);
                existing.setRunNumber(existing.getRunNumber() != null ? existing.getRunNumber() + 1 : 2);
                existing.setTotalGross(BigDecimal.ZERO);
                existing.setTotalIncentives(BigDecimal.ZERO);
                existing.setTotalPenalties(BigDecimal.ZERO);
                existing.setTotalTds(BigDecimal.ZERO);
                existing.setTotalNetPayout(BigDecimal.ZERO);
                existing.setCreatedBy(createdBy != null ? createdBy : "SYSTEM");
                existing.setCreatedAt(Instant.now());
                existing.getSettlements().clear();
                return settlementRunRepository.saveAndFlush(existing);
            } else if (existing.getStatus() == SettlementRunStatus.STARTED) {
                // Recover stale claim abandoned for > 15 minutes
                if (existing.getCreatedAt().isBefore(Instant.now().minusSeconds(900))) {
                    log.warn("Recovering abandoned STALE claim {} (started > 15 minutes ago)", existing.getId());
                    existing.setStatus(SettlementRunStatus.STARTED);
                    existing.setRunNumber(existing.getRunNumber() != null ? existing.getRunNumber() + 1 : 2);
                    existing.setCreatedAt(Instant.now());
                    existing.setCreatedBy(createdBy != null ? createdBy : "SYSTEM");
                    return settlementRunRepository.saveAndFlush(existing);
                }
                throw new com.moveinsync.settlement.exception.ConflictException("Settlement run for this scope is already in progress.");
            }
        }

        SettlementRun run = SettlementRun.builder()
                .id(UUID.randomUUID())
                .vendor(vendor)
                .businessUnit(businessUnit)
                .billingPeriod(billingPeriod)
                .runNumber(1)
                .status(SettlementRunStatus.STARTED)
                .totalGross(BigDecimal.ZERO)
                .totalIncentives(BigDecimal.ZERO)
                .totalPenalties(BigDecimal.ZERO)
                .totalTds(BigDecimal.ZERO)
                .totalNetPayout(BigDecimal.ZERO)
                .createdBy(createdBy != null ? createdBy : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        return settlementRunRepository.saveAndFlush(run);
    }

    /**
     * Transitions a run claim to FAILED status in an independent transaction upon calculation failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunFailed(UUID runId) {
        log.warn("Marking settlement run claim {} as FAILED", runId);
        settlementRunRepository.findById(runId).ifPresent(run -> {
            if (run.getStatus() == SettlementRunStatus.STARTED) {
                run.setStatus(SettlementRunStatus.FAILED);
                settlementRunRepository.saveAndFlush(run);
            }
        });
    }
}
