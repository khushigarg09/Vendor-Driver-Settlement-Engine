package com.moveinsync.settlement.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain Model: Money Financial Precision Tests")
class MoneyTest {

    @Test
    @DisplayName("Should maintain scale of 2 with HALF_UP rounding")
    void testScaleAndRounding() {
        Money m1 = Money.of(new BigDecimal("100.555"));
        assertThat(m1.getAmount()).isEqualByComparingTo("100.56");

        Money m2 = Money.of(new BigDecimal("100.554"));
        assertThat(m2.getAmount()).isEqualByComparingTo("100.55");
    }

    @Test
    @DisplayName("Should perform exact addition without floating point loss")
    void testAdditionPrecision() {
        Money m1 = Money.of("45000.00");
        Money m2 = Money.of("2000.00");
        Money result = m1.add(m2);

        assertThat(result.getAmount()).isEqualByComparingTo("47000.00");
    }

    @Test
    @DisplayName("Should perform signed arithmetic accurately")
    void testSignedSubtraction() {
        Money base = Money.of("45000.00");
        Money incentive = Money.of("2000.00");
        Money penalty = Money.of("500.00");
        Money tds = Money.of("1500.00");

        // Net = Base + Incentive - Penalty - TDS
        Money net = base.add(incentive).subtract(penalty).subtract(tds);

        assertThat(net.getAmount()).isEqualByComparingTo("45000.00");
        assertThat(net.isPositive()).isTrue();
    }

    @Test
    @DisplayName("Should satisfy signed line summation invariant")
    void testSignedLinesSum() {
        // Base (+₹45,000.00) + On-Time (+₹2,000.00) + Penalty (-₹500.00) + TDS (-₹1,500.00)
        Money line1 = Money.of("45000.00");
        Money line2 = Money.of("2000.00");
        Money line3 = Money.of("-500.00");
        Money line4 = Money.of("-1500.00");

        Money sum = line1.add(line2).add(line3).add(line4);
        Money expectedNet = Money.of("45000.00");

        assertThat(sum).isEqualTo(expectedNet);
        assertThat(sum.compareTo(expectedNet)).isEqualTo(0);
    }

    @Test
    @DisplayName("Should correctly compare Money values")
    void testComparisons() {
        Money m1 = Money.of("100.00");
        Money m2 = Money.of("100.00");
        Money m3 = Money.of("200.00");

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.isLessThan(m3)).isTrue();
        assertThat(m3.isGreaterThan(m1)).isTrue();
    }

    @Test
    @DisplayName("Should format currency with rupee symbol")
    void testToStringFormatting() {
        Money money = Money.of("30000.00");
        assertThat(money.toString()).isEqualTo("₹30000.00");
    }

    @Test
    @DisplayName("Should throw NullPointerException when operand is null")
    void testNullGuards() {
        Money money = Money.of("100.00");
        assertThatThrownBy(() -> money.add(null))
                .isInstanceOf(NullPointerException.class);
    }
}
