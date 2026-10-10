package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationRepository extends JpaRepository<Reconciliation, UUID> {
    Optional<Reconciliation> findBySettlementRunIdAndVendorInvoiceId(UUID settlementRunId, UUID vendorInvoiceId);
}
