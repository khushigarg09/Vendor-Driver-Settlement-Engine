package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.SignedLineItem;
import com.moveinsync.settlement.domain.calculation.model.TdsConfig;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CumulativeTdsCalculationEngine {

    public TdsResult calculateTds(
            Money grossEarnings,
            Money previouslyWithheldTax,
            TdsConfig config
    ) {
        Objects.requireNonNull(grossEarnings, "Gross earnings cannot be null");
        Objects.requireNonNull(config, "TdsConfig cannot be null");

        Money priorTax = (previouslyWithheldTax != null) ? previouslyWithheldTax : Money.ZERO;
        List<SignedLineItem> lines = new ArrayList<>();

        if (grossEarnings.isLessThanOrEqualTo(config.monthlyThreshold())) {
            // Below threshold: ₹0.00 tax
            return new TdsResult(Money.ZERO, lines);
        }

        Money taxableExcess = grossEarnings.subtract(config.monthlyThreshold());
        BigDecimal decimalRate = config.getDecimalRate();

        Money cumulativeTaxDue = taxableExcess.multiply(decimalRate);
        Money incrementalTds = cumulativeTaxDue.subtract(priorTax);

        if (incrementalTds.isNegative()) {
            incrementalTds = Money.ZERO;
        }

        if (incrementalTds.isPositive()) {
            lines.add(new SignedLineItem(
                    SettlementLineType.TDS_TAX_WITHHOLDING,
                    incrementalTds.negate(), // Signed deduction (-)
                    String.format("TDS Tax Withheld (%.1f%% on monthly gross exceeding %s threshold)",
                            config.taxRatePercentage().doubleValue(), config.monthlyThreshold()),
                    null
            ));
        }

        return new TdsResult(incrementalTds, lines);
    }

    public record TdsResult(Money tdsWithheld, List<SignedLineItem> lines) {}
}
