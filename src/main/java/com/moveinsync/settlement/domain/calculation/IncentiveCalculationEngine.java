package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.IncentiveConfig;
import com.moveinsync.settlement.domain.calculation.model.SignedLineItem;
import com.moveinsync.settlement.domain.calculation.model.TripRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class IncentiveCalculationEngine {

    public IncentiveResult calculateIncentives(
            List<TripRecord> trips,
            IncentiveConfig config,
            boolean perfectAttendance,
            ZoneId businessZoneId
    ) {
        Objects.requireNonNull(trips, "Trips cannot be null");
        Objects.requireNonNull(config, "IncentiveConfig cannot be null");

        ZoneId zone = (businessZoneId != null) ? businessZoneId : ZoneId.of("Asia/Kolkata");

        List<SignedLineItem> lines = new ArrayList<>();
        Money totalIncentives = Money.ZERO;

        List<TripRecord> completedTrips = trips.stream()
                .filter(TripRecord::isCompleted)
                .toList();

        if (completedTrips.isEmpty()) {
            return new IncentiveResult(Money.ZERO, lines);
        }

        // 1. On-Time Arrival Bonus
        long onTimeCount = completedTrips.stream().filter(TripRecord::isOnTime).count();
        BigDecimal onTimeRatio = BigDecimal.valueOf(onTimeCount)
                .divide(BigDecimal.valueOf(completedTrips.size()), 4, RoundingMode.HALF_UP);

        if (onTimeRatio.compareTo(config.onTimeRatioThreshold()) >= 0) {
            Money bonus = config.onTimeBonusAmount();
            totalIncentives = totalIncentives.add(bonus);
            lines.add(new SignedLineItem(
                    SettlementLineType.ON_TIME_BONUS,
                    bonus,
                    String.format("Incentive: On-Time Performance (%.1f%% >= %.1f%% SLA)",
                            onTimeRatio.multiply(BigDecimal.valueOf(100)).doubleValue(),
                            config.onTimeRatioThreshold().multiply(BigDecimal.valueOf(100)).doubleValue()),
                    null
            ));
        }

        // 2. Worked Days Monthly Bonus
        Set<LocalDate> workedDays = new HashSet<>();
        for (TripRecord trip : completedTrips) {
            workedDays.add(trip.completedAt().atZone(zone).toLocalDate());
        }

        int daysWorked = workedDays.size();
        if (daysWorked >= config.tier1WorkedDaysThreshold()) {
            Money bonus = config.tier1WorkedDaysBonus();
            totalIncentives = totalIncentives.add(bonus);
            lines.add(new SignedLineItem(
                    SettlementLineType.DAYS_WORKED_BONUS,
                    bonus,
                    String.format("Incentive: Tier 1 Worked Days Bonus (%d >= %d days)",
                            daysWorked, config.tier1WorkedDaysThreshold()),
                    null
            ));
        } else if (daysWorked >= config.tier2WorkedDaysThreshold()) {
            Money bonus = config.tier2WorkedDaysBonus();
            totalIncentives = totalIncentives.add(bonus);
            lines.add(new SignedLineItem(
                    SettlementLineType.DAYS_WORKED_BONUS,
                    bonus,
                    String.format("Incentive: Tier 2 Worked Days Bonus (%d >= %d days)",
                            daysWorked, config.tier2WorkedDaysThreshold()),
                    null
            ));
        }

        // 3. Attendance Bonus
        if (perfectAttendance && config.attendanceBonusAmount().isPositive()) {
            Money bonus = config.attendanceBonusAmount();
            totalIncentives = totalIncentives.add(bonus);
            lines.add(new SignedLineItem(
                    SettlementLineType.ATTENDANCE_BONUS,
                    bonus,
                    "Incentive: 100% Shift Attendance Bonus (Zero unexcused absences)",
                    null
            ));
        }

        return new IncentiveResult(totalIncentives, lines);
    }

    public record IncentiveResult(Money totalIncentives, List<SignedLineItem> lines) {}
}
