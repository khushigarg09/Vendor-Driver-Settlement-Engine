package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.IncentiveConfig;
import com.moveinsync.settlement.domain.calculation.model.TripRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.TripStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Calculation Engine: IncentiveCalculationEngine Unit Tests")
class IncentiveCalculationEngineTest {

    private final IncentiveCalculationEngine engine = new IncentiveCalculationEngine();
    private final IncentiveConfig config = IncentiveConfig.defaultConfig();
    private final UUID driverId = UUID.randomUUID();

    @Test
    @DisplayName("On-time bonus awarded when SLA >= 95%")
    void testOnTimeBonusAwarded() {
        List<TripRecord> trips = new ArrayList<>();
        // 95 on-time trips, 5 delayed trips = 95% SLA
        for (int i = 0; i < 95; i++) {
            trips.add(createTrip("TRIP-" + i, Instant.parse("2026-10-01T10:00:00Z"), 0));
        }
        for (int i = 95; i < 100; i++) {
            trips.add(createTrip("TRIP-" + i, Instant.parse("2026-10-01T12:00:00Z"), 10)); // delayed
        }

        var result = engine.calculateIncentives(trips, config, false, ZoneId.of("Asia/Kolkata"));

        assertThat(result.totalIncentives()).isEqualTo(Money.of("2000.00"));
        assertThat(result.lines()).hasSize(1);
    }

    @Test
    @DisplayName("On-time bonus NOT awarded when SLA < 95%")
    void testOnTimeBonusNotAwarded() {
        List<TripRecord> trips = new ArrayList<>();
        // 90 on-time trips, 10 delayed trips = 90% SLA
        for (int i = 0; i < 90; i++) {
            trips.add(createTrip("TRIP-" + i, Instant.parse("2026-10-01T10:00:00Z"), 0));
        }
        for (int i = 90; i < 100; i++) {
            trips.add(createTrip("TRIP-" + i, Instant.parse("2026-10-01T12:00:00Z"), 5));
        }

        var result = engine.calculateIncentives(trips, config, false, ZoneId.of("Asia/Kolkata"));

        assertThat(result.totalIncentives()).isEqualTo(Money.ZERO);
        assertThat(result.lines()).isEmpty();
    }

    @Test
    @DisplayName("Worked days Tier 1 bonus awarded for >= 26 active working days")
    void testWorkedDaysTier1Bonus() {
        List<TripRecord> trips = new ArrayList<>();
        // 26 distinct calendar days
        for (int day = 1; day <= 26; day++) {
            String dayStr = (day < 10) ? "0" + day : String.valueOf(day);
            trips.add(createTrip("TRIP-D" + day, Instant.parse("2026-10-" + dayStr + "T10:00:00Z"), 0));
        }

        var result = engine.calculateIncentives(trips, config, false, ZoneId.of("Asia/Kolkata"));

        // On-time bonus (100% SLA -> ₹2000) + Tier 1 worked days bonus (26 days -> ₹3000) = ₹5000
        assertThat(result.totalIncentives()).isEqualTo(Money.of("5000.00"));
        assertThat(result.lines()).hasSize(2);
    }

    @Test
    @DisplayName("Attendance bonus awarded when perfectAttendance is true")
    void testAttendanceBonusAwarded() {
        List<TripRecord> trips = List.of(createTrip("TRIP-1", Instant.parse("2026-10-01T10:00:00Z"), 15)); // delayed, 1 day

        var result = engine.calculateIncentives(trips, config, true, ZoneId.of("Asia/Kolkata"));

        // Only attendance bonus: ₹1,000.00
        assertThat(result.totalIncentives()).isEqualTo(Money.of("1000.00"));
        assertThat(result.lines()).hasSize(1);
    }

    private TripRecord createTrip(String sourceId, Instant completedAt, int delayMinutes) {
        return new TripRecord(
                UUID.randomUUID(), sourceId, driverId, "SEDAN",
                completedAt.minusSeconds(3600), completedAt, delayMinutes, TripStatus.COMPLETED
        );
    }
}
