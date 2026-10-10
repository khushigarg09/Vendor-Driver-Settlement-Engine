package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.SignedLineItem;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;
import com.moveinsync.settlement.exception.FinancialInvariantException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Calculation Engine: NetPayslipReconciler Unit Tests")
class NetPayslipReconcilerTest {

    private final NetPayslipReconciler reconciler = new NetPayslipReconciler();

    @Test
    @DisplayName("Reconciles successfully when signed lines sum identically to net payout")
    void testReconciliationSuccess() {
        List<SignedLineItem> lines = List.of(
                new SignedLineItem(SettlementLineType.BASE_PAY, Money.of("45000.00"), "Base Pay", null),
                new SignedLineItem(SettlementLineType.ATTENDANCE_BONUS, Money.of("2000.00"), "Attendance Bonus", null),
                new SignedLineItem(SettlementLineType.OVER_SPEED_PENALTY, Money.of("-500.00"), "Speeding Violation", null),
                new SignedLineItem(SettlementLineType.TDS_TAX_WITHHOLDING, Money.of("-850.00"), "TDS Deduction @ 5%", null)
        );

        // Expected Net: 45000 + 2000 - 500 - 850 = 45650.00
        Money netPayout = Money.of("45650.00");

        assertThatCode(() -> reconciler.reconcileOrThrow(lines, netPayout))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws FinancialInvariantException when there is a 1-paisa drift")
    void testReconciliationFailsOnOnePaisaDrift() {
        List<SignedLineItem> lines = List.of(
                new SignedLineItem(SettlementLineType.BASE_PAY, Money.of("100.00"), "Base Pay", null)
        );

        Money incorrectNet = Money.of("100.01");

        assertThatThrownBy(() -> reconciler.reconcileOrThrow(lines, incorrectNet))
                .isInstanceOf(FinancialInvariantException.class)
                .hasMessageContaining("CRITICAL: Payslip line items do not sum to net payout")
                .hasMessageContaining("Expected Net: ₹100.01")
                .hasMessageContaining("Sum of Lines: ₹100.00")
                .hasMessageContaining("Delta: ₹0.01");
    }

    @Test
    @DisplayName("Reconciles zero lines to zero net payout")
    void testEmptyLinesReconciliation() {
        assertThatCode(() -> reconciler.reconcileOrThrow(List.of(), Money.ZERO))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Throws NullPointerException when parameters are null")
    void testNullValidation() {
        assertThatThrownBy(() -> reconciler.reconcileOrThrow(null, Money.ZERO))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> reconciler.reconcileOrThrow(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
