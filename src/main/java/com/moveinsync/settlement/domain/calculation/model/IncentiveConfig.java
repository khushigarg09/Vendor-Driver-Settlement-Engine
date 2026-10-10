package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;

import java.math.BigDecimal;

public record IncentiveConfig(
        BigDecimal onTimeRatioThreshold, // e.g. 0.95
        Money onTimeBonusAmount,          // e.g. ₹2000.00
        int tier1WorkedDaysThreshold,     // e.g. 26
        Money tier1WorkedDaysBonus,       // e.g. ₹3000.00
        int tier2WorkedDaysThreshold,     // e.g. 22
        Money tier2WorkedDaysBonus,       // e.g. ₹1500.00
        Money attendanceBonusAmount       // e.g. ₹1000.00
) {
    public static IncentiveConfig defaultConfig() {
        return new IncentiveConfig(
                new BigDecimal("0.95"),
                Money.of("2000.00"),
                26,
                Money.of("3000.00"),
                22,
                Money.of("1500.00"),
                Money.of("1000.00")
        );
    }
}
