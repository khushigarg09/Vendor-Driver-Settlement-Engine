package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.ContractRateRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.exception.MissingContractRateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Calculation Engine: RateResolver Unit Tests")
class RateResolverTest {

    private final RateResolver rateResolver = new RateResolver();
    private final UUID contractId = UUID.randomUUID();

    private final List<ContractRateRecord> rateCards = List.of(
            new ContractRateRecord(
                    UUID.randomUUID(), contractId, "SEDAN",
                    Money.of("250.00"),
                    Instant.parse("2026-10-01T00:00:00Z"),
                    Instant.parse("2026-10-15T00:00:00Z")
            ),
            new ContractRateRecord(
                    UUID.randomUUID(), contractId, "SEDAN",
                    Money.of("280.00"),
                    Instant.parse("2026-10-15T00:00:00Z"),
                    null
            ),
            new ContractRateRecord(
                    UUID.randomUUID(), contractId, "SUV",
                    Money.of("400.00"),
                    Instant.parse("2026-10-01T00:00:00Z"),
                    null
            )
    );

    @Test
    @DisplayName("Trip on Oct 10th (before change) resolves to ₹250.00")
    void testResolveBeforeRateChange() {
        Money rate = rateResolver.resolveRate(rateCards, "SEDAN", Instant.parse("2026-10-10T12:00:00Z"));
        assertThat(rate).isEqualTo(Money.of("250.00"));
    }

    @Test
    @DisplayName("Trip on Oct 15th 00:00:00 (exact change boundary) resolves to ₹280.00")
    void testResolveOnRateChangeBoundary() {
        Money rate = rateResolver.resolveRate(rateCards, "SEDAN", Instant.parse("2026-10-15T00:00:00Z"));
        assertThat(rate).isEqualTo(Money.of("280.00"));
    }

    @Test
    @DisplayName("Trip on Oct 20th (after change) resolves to ₹280.00")
    void testResolveAfterRateChange() {
        Money rate = rateResolver.resolveRate(rateCards, "SEDAN", Instant.parse("2026-10-20T15:30:00Z"));
        assertThat(rate).isEqualTo(Money.of("280.00"));
    }

    @Test
    @DisplayName("Different vehicle category (SUV) resolves to its own rate card")
    void testResolveDifferentCategory() {
        Money rate = rateResolver.resolveRate(rateCards, "SUV", Instant.parse("2026-10-10T12:00:00Z"));
        assertThat(rate).isEqualTo(Money.of("400.00"));
    }

    @Test
    @DisplayName("Missing rate throws MissingContractRateException")
    void testMissingRateThrowsException() {
        assertThatThrownBy(() -> rateResolver.resolveRate(rateCards, "HATCHBACK", Instant.parse("2026-10-10T12:00:00Z")))
                .isInstanceOf(MissingContractRateException.class)
                .hasMessageContaining("No applicable contract rate found");
    }
}
