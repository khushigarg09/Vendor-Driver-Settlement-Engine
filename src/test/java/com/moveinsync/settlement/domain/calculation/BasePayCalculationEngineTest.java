package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.ContractRateRecord;
import com.moveinsync.settlement.domain.calculation.model.TripRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.BillingType;
import com.moveinsync.settlement.entity.enums.TripStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Calculation Engine: BasePayCalculationEngine Unit Tests")
class BasePayCalculationEngineTest {

    private final BasePayCalculationEngine engine = new BasePayCalculationEngine(new RateResolver());
    private final UUID contractId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

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
            )
    );

    @Test
    @DisplayName("PER_TRIP: Calculates base pay honoring mid-month rate changes")
    void testPerTripMidMonthRateChange() {
        TripRecord tripBefore = new TripRecord(
                UUID.randomUUID(), "TRIP-01", driverId, "SEDAN",
                Instant.parse("2026-10-10T08:00:00Z"), Instant.parse("2026-10-10T09:00:00Z"),
                0, TripStatus.COMPLETED
        );

        TripRecord tripAfter = new TripRecord(
                UUID.randomUUID(), "TRIP-02", driverId, "SEDAN",
                Instant.parse("2026-10-16T18:00:00Z"), Instant.parse("2026-10-16T19:00:00Z"),
                0, TripStatus.COMPLETED
        );

        var result = engine.calculateBasePay(
                List.of(tripBefore, tripAfter), rateCards, BillingType.PER_TRIP, ZoneId.of("Asia/Kolkata")
        );

        // ₹250.00 + ₹280.00 = ₹530.00
        assertThat(result.totalBasePay()).isEqualTo(Money.of("530.00"));
        assertThat(result.eligibleTripCount()).isEqualTo(2);
        assertThat(result.lines()).hasSize(2);
    }

    @Test
    @DisplayName("Cancelled trips are excluded from base pay")
    void testCancelledTripsExcluded() {
        TripRecord cancelledTrip = new TripRecord(
                UUID.randomUUID(), "TRIP-CANCEL", driverId, "SEDAN",
                Instant.parse("2026-10-10T08:00:00Z"), Instant.parse("2026-10-10T09:00:00Z"),
                0, TripStatus.CANCELLED_BY_PASSENGER
        );

        var result = engine.calculateBasePay(
                List.of(cancelledTrip), rateCards, BillingType.PER_TRIP, ZoneId.of("Asia/Kolkata")
        );

        assertThat(result.totalBasePay()).isEqualTo(Money.ZERO);
        assertThat(result.eligibleTripCount()).isEqualTo(0);
        assertThat(result.lines()).isEmpty();
    }

    @Test
    @DisplayName("PER_DUTY: Multiple trips on same calendar duty date pay 1 duty rate")
    void testPerDutyAggregation() {
        // 2 trips on same day: 2026-10-10
        TripRecord trip1 = new TripRecord(
                UUID.randomUUID(), "TRIP-D1-1", driverId, "SEDAN",
                Instant.parse("2026-10-10T02:00:00Z"), Instant.parse("2026-10-10T03:00:00Z"),
                0, TripStatus.COMPLETED
        );
        TripRecord trip2 = new TripRecord(
                UUID.randomUUID(), "TRIP-D1-2", driverId, "SEDAN",
                Instant.parse("2026-10-10T08:00:00Z"), Instant.parse("2026-10-10T09:00:00Z"),
                0, TripStatus.COMPLETED
        );

        // 1 trip on next day: 2026-10-11
        TripRecord trip3 = new TripRecord(
                UUID.randomUUID(), "TRIP-D2-1", driverId, "SEDAN",
                Instant.parse("2026-10-11T08:00:00Z"), Instant.parse("2026-10-11T09:00:00Z"),
                0, TripStatus.COMPLETED
        );

        var result = engine.calculateBasePay(
                List.of(trip1, trip2, trip3), rateCards, BillingType.PER_DUTY, ZoneId.of("Asia/Kolkata")
        );

        // 2 duty days @ ₹250.00 = ₹500.00
        assertThat(result.totalBasePay()).isEqualTo(Money.of("500.00"));
        assertThat(result.lines()).hasSize(2);
    }
}
