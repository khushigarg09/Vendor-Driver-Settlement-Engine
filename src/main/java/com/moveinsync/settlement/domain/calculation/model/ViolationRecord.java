package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.ViolationType;

import java.time.Instant;
import java.util.UUID;

public record ViolationRecord(
        UUID id,
        String sourceViolationId,
        UUID driverId,
        ViolationType violationType,
        Instant occurredAt,
        Money penaltyAmount
) {}
