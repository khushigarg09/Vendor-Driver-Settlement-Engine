package com.moveinsync.settlement.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable value object representing financial currency with exact decimal precision.
 * Enforces:
 * - Scale: exactly 2 decimal places (Rupees and Paise)
 * - Rounding Mode: RoundingMode.HALF_UP
 * - Prohibits IEEE 754 floating-point drift
 */
public final class Money implements Comparable<Money>, Serializable {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static final Money ZERO = new Money(BigDecimal.ZERO);
    public static final Money ONE = new Money(BigDecimal.ONE);

    private final BigDecimal amount;

    private Money(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        this.amount = amount.setScale(SCALE, ROUNDING_MODE);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public static Money of(long rupees) {
        return new Money(BigDecimal.valueOf(rupees));
    }

    public static Money of(String amountString) {
        Objects.requireNonNull(amountString, "Amount string cannot be null");
        return new Money(new BigDecimal(amountString));
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "Cannot add null Money");
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "Cannot subtract null Money");
        return new Money(this.amount.subtract(other.amount));
    }

    public Money multiply(long factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "Factor cannot be null");
        return new Money(this.amount.multiply(factor).setScale(SCALE, ROUNDING_MODE));
    }

    public Money negate() {
        return new Money(this.amount.negate());
    }

    public Money abs() {
        return new Money(this.amount.abs());
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isGreaterThan(Money other) {
        return this.compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return this.compareTo(other) < 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.compareTo(other) >= 0;
    }

    public boolean isLessThanOrEqualTo(Money other) {
        return this.compareTo(other) <= 0;
    }

    @Override
    public int compareTo(Money other) {
        Objects.requireNonNull(other, "Cannot compare to null Money");
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return this.amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "₹" + this.amount.toPlainString();
    }
}
