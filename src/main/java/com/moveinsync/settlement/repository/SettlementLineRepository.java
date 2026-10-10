package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementLineRepository extends JpaRepository<SettlementLine, UUID> {
    List<SettlementLine> findBySettlementId(UUID settlementId);
}
