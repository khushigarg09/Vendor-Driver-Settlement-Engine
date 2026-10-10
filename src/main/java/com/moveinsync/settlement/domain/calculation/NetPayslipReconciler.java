package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.SignedLineItem;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.exception.FinancialInvariantException;

import java.util.List;
import java.util.Objects;

public class NetPayslipReconciler {

    /**
     * Verifies that the exact sum of all signed lines matches the net payout to the exact paisa.
     * Throws FinancialInvariantException if any mismatch exists.
     */
    public void reconcileOrThrow(List<SignedLineItem> lines, Money netPayout) {
        Objects.requireNonNull(lines, "Lines cannot be null");
        Objects.requireNonNull(netPayout, "Net payout cannot be null");

        Money sum = Money.ZERO;
        for (SignedLineItem line : lines) {
            sum = sum.add(line.signedAmount());
        }

        if (sum.compareTo(netPayout) != 0) {
            throw new FinancialInvariantException(String.format(
                    "CRITICAL: Payslip line items do not sum to net payout! Expected Net: %s, Sum of Lines: %s, Delta: %s",
                    netPayout, sum, netPayout.subtract(sum)
            ));
        }
    }
}
