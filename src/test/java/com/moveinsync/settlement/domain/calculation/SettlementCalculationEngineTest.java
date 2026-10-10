package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.*;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.BillingType;
import com.moveinsync.settlement.entity.enums.TripStatus;
import com.moveinsync.settlement.entity.enums.ViolationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Calculation Engine: SettlementCalculationEngine Integration & Invariant Tests")
class SettlementCalculationEngineTest {

    private final SettlementCalculationEngine engine = SettlementCalculationEngine.createDefault();
    private final ZoneId zoneId = ZoneId.of("Asia/Kolkata");
    private final UUID driverId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    private final List<ContractRateRecord> contractRates = List.of(
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
                    Money.of("350.00"),
                    Instant.parse("2026-10-01T00:00:00Z"),
                    null
            )
    );

    @Test
    @DisplayName("Case Study Baseline: 180 trips with mid-month rate change, tier incentives, penalties, and TDS")
    void testCaseStudy180TripsFullCalculation() {
        // 90 trips before Oct 15 (Rate: ₹250.00) = ₹22,500.00
        // 90 trips after Oct 15 (Rate: ₹280.00) = ₹25,200.00
        // Total Base Pay = ₹47,700.00
        List<TripRecord> trips = new ArrayList<>();
        Instant beforeOct15 = Instant.parse("2026-10-05T08:00:00Z");
        for (int i = 1; i <= 90; i++) {
            trips.add(new TripRecord(
                    UUID.randomUUID(), "TRIP-OCT-PRE-" + i, driverId, "SEDAN",
                    beforeOct15.plus(Duration.ofMinutes(i * 30L)),
                    beforeOct15.plus(Duration.ofMinutes(i * 30L + 25L)),
                    0, TripStatus.COMPLETED
            ));
        }

        Instant afterOct15 = Instant.parse("2026-10-18T08:00:00Z");
        for (int i = 1; i <= 90; i++) {
            trips.add(new TripRecord(
                    UUID.randomUUID(), "TRIP-OCT-POST-" + i, driverId, "SEDAN",
                    afterOct15.plus(Duration.ofMinutes(i * 30L)),
                    afterOct15.plus(Duration.ofMinutes(i * 30L + 25L)),
                    0, TripStatus.COMPLETED
            ));
        }

        // 2 Violations: Speeding (₹500.00) + Route deviation (₹200.00) = ₹700.00
        List<ViolationRecord> violations = List.of(
                new ViolationRecord(UUID.randomUUID(), "V-01", driverId, ViolationType.OVER_SPEED, Instant.parse("2026-10-08T10:00:00Z"), Money.of("500.00")),
                new ViolationRecord(UUID.randomUUID(), "V-02", driverId, ViolationType.ROUTE_DEVIATION, Instant.parse("2026-10-20T07:30:00Z"), Money.of("200.00"))
        );

        DriverSettlementCalculationResult result = engine.computeDriverSettlement(
                driverId,
                "2026-10",
                trips,
                contractRates,
                violations,
                BillingType.PER_TRIP,
                IncentiveConfig.defaultConfig(),
                PenaltyConfig.defaultConfig(),
                TdsConfig.defaultConfig(),
                Money.ZERO,
                true, // perfect attendance
                zoneId
        );

        // Assertions:
        assertThat(result.driverId()).isEqualTo(driverId);
        assertThat(result.billingPeriod()).isEqualTo("2026-10");
        assertThat(result.tripsCount()).isEqualTo(180);

        // 1. Base Pay: 90 * 250 (22,500) + 90 * 280 (25,200) = 47,700.00
        assertThat(result.grossBasePay()).isEqualTo(Money.of("47700.00"));

        // 2. Incentives: 2,000 (On-time bonus) + 1,000 (Attendance bonus) = 3,000.00
        assertThat(result.incentivesTotal()).isEqualTo(Money.of("3000.00"));

        // Gross Earnings = 47,700 + 3,000 = 50,700.00
        // 3. Penalties: 500 + 200 = 700.00
        assertThat(result.penaltiesTotal()).isEqualTo(Money.of("700.00"));

        // 4. TDS: (50,700.00 - 30,000.00) * 0.05 = 20,700.00 * 0.05 = 1,035.00
        assertThat(result.tdsTaxTotal()).isEqualTo(Money.of("1035.00"));

        // 5. Net Payout: 47,700 + 3,000 - 700 - 1,035 = 48,965.00
        Money expectedNet = Money.of("48965.00");
        assertThat(result.netPayout()).isEqualTo(expectedNet);

        // 6. Enforce Line Sum Reconciles to Exact Paisa
        Money sumLines = Money.ZERO;
        for (SignedLineItem line : result.lineItems()) {
            sumLines = sumLines.add(line.signedAmount());
        }
        assertThat(sumLines).isEqualTo(expectedNet);
    }

    @Test
    @DisplayName("Empty Trips: Driver with 0 trips has zero gross, zero TDS, and zero net payout")
    void testEmptyTripsCalculation() {
        DriverSettlementCalculationResult result = engine.computeDriverSettlement(
                driverId,
                "2026-10",
                List.of(),
                contractRates,
                List.of(),
                BillingType.PER_TRIP,
                IncentiveConfig.defaultConfig(),
                PenaltyConfig.defaultConfig(),
                TdsConfig.defaultConfig(),
                Money.ZERO,
                false,
                zoneId
        );

        assertThat(result.tripsCount()).isEqualTo(0);
        assertThat(result.grossBasePay()).isEqualTo(Money.ZERO);
        assertThat(result.incentivesTotal()).isEqualTo(Money.ZERO);
        assertThat(result.penaltiesTotal()).isEqualTo(Money.ZERO);
        assertThat(result.tdsTaxTotal()).isEqualTo(Money.ZERO);
        assertThat(result.netPayout()).isEqualTo(Money.ZERO);
        assertThat(result.lineItems()).isEmpty();
    }

    @Test
    @DisplayName("Scale Test: 5,000 synthetic trips calculate deterministically within 1000ms with exact paisa reconciliation")
    void testScaleFiveThousandTrips() {
        List<TripRecord> trips = new ArrayList<>(5000);
        Instant start = Instant.parse("2026-10-01T00:00:00Z");

        for (int i = 0; i < 5000; i++) {
            // Half before Oct 15, half after Oct 15
            Instant tripTime = start.plus(Duration.ofMinutes(i * 8L));
            trips.add(new TripRecord(
                    UUID.randomUUID(),
                    "SCALE-TRIP-" + i,
                    driverId,
                    "SEDAN",
                    tripTime,
                    tripTime.plus(Duration.ofMinutes(20)),
                    0,
                    TripStatus.COMPLETED
            ));
        }

        long startTime = System.currentTimeMillis();
        DriverSettlementCalculationResult result = engine.computeDriverSettlement(
                driverId,
                "2026-10",
                trips,
                contractRates,
                List.of(),
                BillingType.PER_TRIP,
                IncentiveConfig.defaultConfig(),
                PenaltyConfig.defaultConfig(),
                TdsConfig.defaultConfig(),
                Money.ZERO,
                false,
                zoneId
        );
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertThat(result.tripsCount()).isEqualTo(5000);
        assertThat(elapsedMs).isLessThan(1000); // Super fast in-memory financial calculation

        // Mathematical invariant check
        Money sumLines = Money.ZERO;
        for (SignedLineItem line : result.lineItems()) {
            sumLines = sumLines.add(line.signedAmount());
        }
        assertThat(sumLines).isEqualTo(result.netPayout());
    }
}
