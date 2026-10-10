package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

public record ContractRateRecord(
        UUID id,
        UUID contractId,
        String vehicleCategory,
        Money rateAmount,
        Instant effectiveFrom,
        Instant effectiveTo
) {
    public boolean isApplicableAt(Instant timestamp, String category) {
        if (!this.vehicleCategory.equalsIgnoreCase(category)) {
            return false;
        }
        if (timestamp.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || timestamp.isBefore(effectiveTo);
    }
}
