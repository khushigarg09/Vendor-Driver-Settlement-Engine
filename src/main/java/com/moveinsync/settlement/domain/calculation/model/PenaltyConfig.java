package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;

public record PenaltyConfig(
        Money overSpeedDefaultPenalty,      // e.g. ₹500.00
        Money routeDeviationDefaultPenalty, // e.g. ₹500.00
        Money nightSafetyDefaultPenalty,    // e.g. ₹1000.00
        Money monthlyPenaltyCap             // e.g. ₹5000.00 (Nullable / optional)
) {
    public static PenaltyConfig defaultConfig() {
        return new PenaltyConfig(
                Money.of("500.00"),
                Money.of("500.00"),
                Money.of("1000.00"),
                Money.of("5000.00")
        );
    }
}
