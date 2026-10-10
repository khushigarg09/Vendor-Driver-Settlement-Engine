package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.TdsConfig;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Calculation Engine: CumulativeTdsCalculationEngine Unit Tests")
class CumulativeTdsCalculationEngineTest {

    private final CumulativeTdsCalculationEngine engine = new CumulativeTdsCalculationEngine();
    private final TdsConfig config = new TdsConfig(Money.of("30000.00"), new BigDecimal("5.00"));

    @Test
    @DisplayName("Gross earnings below threshold incurs ₹0 TDS")
    void testBelowThreshold() {
        Money gross = Money.of("28500.00");
        var result = engine.calculateTds(gross, Money.ZERO, config);

        assertThat(result.tdsWithheld()).isEqualTo(Money.ZERO);
        assertThat(result.lines()).isEmpty();
    }

    @Test
    @DisplayName("Gross earnings exactly equal to threshold incurs ₹0 TDS")
    void testEqualToThreshold() {
        Money gross = Money.of("30000.00");
        var result = engine.calculateTds(gross, Money.ZERO, config);

        assertThat(result.tdsWithheld()).isEqualTo(Money.ZERO);
        assertThat(result.lines()).isEmpty();
    }

    @Test
    @DisplayName("Gross earnings above threshold calculates 5% on excess")
    void testAboveThreshold() {
        // Gross ₹50,900 - Threshold ₹30,000 = Taxable ₹20,900. 5% of ₹20,900 = ₹1,045.00
        Money gross = Money.of("50900.00");
        var result = engine.calculateTds(gross, Money.ZERO, config);

        assertThat(result.tdsWithheld()).isEqualTo(Money.of("1045.00"));
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).lineType()).isEqualTo(SettlementLineType.TDS_TAX_WITHHOLDING);
        assertThat(result.lines().get(0).signedAmount()).isEqualTo(Money.of("-1045.00"));
    }

    @Test
    @DisplayName("Deducts previously withheld tax to calculate incremental TDS only")
    void testIncrementalTdsWithPriorWithholding() {
        // Cumulative Gross ₹45,000 -> Cumulative Tax = (45,000 - 30,000) * 5% = ₹750.00
        // Previously withheld in earlier run: ₹250.00
        // Incremental TDS due = ₹750 - ₹250 = ₹500.00
        Money gross = Money.of("45000.00");
        Money previouslyWithheld = Money.of("250.00");

        var result = engine.calculateTds(gross, previouslyWithheld, config);

        assertThat(result.tdsWithheld()).isEqualTo(Money.of("500.00"));
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).signedAmount()).isEqualTo(Money.of("-500.00"));
    }
}
