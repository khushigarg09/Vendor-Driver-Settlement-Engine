package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;

import java.math.BigDecimal;

public record TdsConfig(
        Money monthlyThreshold,       // e.g. ₹30,000.00
        BigDecimal taxRatePercentage  // e.g. 5.00
) {
    public static TdsConfig defaultConfig() {
        return new TdsConfig(
                Money.of("30000.00"),
                new BigDecimal("5.00")
        );
    }

    public BigDecimal getDecimalRate() {
        return taxRatePercentage.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
    }
}
