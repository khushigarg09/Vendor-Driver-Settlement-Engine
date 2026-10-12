package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.PenaltyConfig;
import com.moveinsync.settlement.domain.calculation.model.ViolationRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;
import com.moveinsync.settlement.entity.enums.ViolationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Calculation Engine: PenaltyCalculationEngine Unit Tests")
class PenaltyCalculationEngineTest {

    private final PenaltyCalculationEngine engine = new PenaltyCalculationEngine();
    private final UUID driverId = UUID.randomUUID();

    @Test
    @DisplayName("Maps violations to signed negative lines")
    void testBasicViolations() {
        ViolationRecord v1 = new ViolationRecord(
                UUID.randomUUID(), "VIO-1", driverId, ViolationType.OVER_SPEED,
                Instant.parse("2026-10-05T14:00:00Z"), Money.of("500.00")
        );
        ViolationRecord v2 = new ViolationRecord(
                UUID.randomUUID(), "VIO-2", driverId, ViolationType.NIGHT_SAFETY_BREACH,
                Instant.parse("2026-10-10T23:00:00Z"), Money.of("1000.00")
        );

        PenaltyConfig config = PenaltyConfig.defaultConfig();
        var result = engine.calculatePenalties(List.of(v1, v2), config);

        // ₹500 + ₹1000 = ₹1500
        assertThat(result.totalPenalties()).isEqualTo(Money.of("1500.00"));
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).signedAmount()).isEqualTo(Money.of("-500.00"));
        assertThat(result.lines().get(0).lineType()).isEqualTo(SettlementLineType.OVER_SPEED_PENALTY);
        assertThat(result.lines().get(1).signedAmount()).isEqualTo(Money.of("-1000.00"));
        assertThat(result.lines().get(1).lineType()).isEqualTo(SettlementLineType.NIGHT_SAFETY_PENALTY);
    }

    @Test
    @DisplayName("Enforces contractual monthly penalty cap when total deductions exceed cap")
    void testPenaltyCapEnforcement() {
        List<ViolationRecord> violations = new ArrayList<>();
        // 12 violations @ ₹500 each = ₹6,000.00 uncapped
        for (int i = 0; i < 12; i++) {
            violations.add(new ViolationRecord(
                    UUID.randomUUID(), "VIO-" + i, driverId, ViolationType.OVER_SPEED,
                    Instant.parse("2026-10-05T14:00:00Z"), Money.of("500.00")
            ));
        }

        // Cap configured at ₹5,000.00
        PenaltyConfig config = new PenaltyConfig(
                Money.of("500.00"), Money.of("500.00"), Money.of("1000.00"), Money.of("5000.00")
        );

        var result = engine.calculatePenalties(violations, config);

        // Total effective penalty is capped at ₹5,000.00
        assertThat(result.totalPenalties()).isEqualTo(Money.of("5000.00"));
        // 12 penalty lines + 1 cap credit adjustment line (+₹1000)
        assertThat(result.lines()).hasSize(13);
        assertThat(result.lines().get(12).lineType()).isEqualTo(SettlementLineType.PENALTY_CAP_ADJUSTMENT);
        assertThat(result.lines().get(12).signedAmount()).isEqualTo(Money.of("1000.00"));

        // Net sum of all penalty lines must equal -₹5,000.00
        Money netPenaltyLineSum = Money.ZERO;
        for (var line : result.lines()) {
            netPenaltyLineSum = netPenaltyLineSum.add(line.signedAmount());
        }
        assertThat(netPenaltyLineSum).isEqualTo(Money.of("-5000.00"));
    }
}
