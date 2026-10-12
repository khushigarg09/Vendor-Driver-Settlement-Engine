package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.PenaltyConfig;
import com.moveinsync.settlement.domain.calculation.model.SignedLineItem;
import com.moveinsync.settlement.domain.calculation.model.ViolationRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;

import java.util.*;

public class PenaltyCalculationEngine {

    public PenaltyResult calculatePenalties(
            List<ViolationRecord> violations,
            PenaltyConfig config
    ) {
        Objects.requireNonNull(violations, "Violations cannot be null");
        Objects.requireNonNull(config, "PenaltyConfig cannot be null");

        List<SignedLineItem> lines = new ArrayList<>();
        Money totalUncappedPenalties = Money.ZERO;

        List<ViolationRecord> sortedViolations = violations.stream()
                .sorted(Comparator.comparing(ViolationRecord::occurredAt))
                .toList();

        for (ViolationRecord violation : sortedViolations) {
            Money penalty = (violation.penaltyAmount() != null && violation.penaltyAmount().isPositive())
                    ? violation.penaltyAmount()
                    : switch (violation.violationType()) {
                        case OVER_SPEED -> config.overSpeedDefaultPenalty();
                        case ROUTE_DEVIATION -> config.routeDeviationDefaultPenalty();
                        case NIGHT_SAFETY_BREACH -> config.nightSafetyDefaultPenalty();
                    };

            totalUncappedPenalties = totalUncappedPenalties.add(penalty);

            SettlementLineType lineType = switch (violation.violationType()) {
                case OVER_SPEED -> SettlementLineType.OVER_SPEED_PENALTY;
                case ROUTE_DEVIATION -> SettlementLineType.ROUTE_DEVIATION_PENALTY;
                case NIGHT_SAFETY_BREACH -> SettlementLineType.NIGHT_SAFETY_PENALTY;
            };

            // Deductions are signed negative lines
            lines.add(new SignedLineItem(
                    lineType,
                    penalty.negate(),
                    String.format("Penalty: %s (Event %s)", violation.violationType(), violation.sourceViolationId()),
                    violation.sourceViolationId()
            ));
        }

        Money effectivePenalty = totalUncappedPenalties;

        // Apply contractual monthly penalty cap if configured
        if (config.monthlyPenaltyCap() != null && config.monthlyPenaltyCap().isPositive()) {
            if (totalUncappedPenalties.isGreaterThan(config.monthlyPenaltyCap())) {
                Money capOffset = totalUncappedPenalties.subtract(config.monthlyPenaltyCap());
                effectivePenalty = config.monthlyPenaltyCap();

                // Add signed credit line for contractual cap waiver
                lines.add(new SignedLineItem(
                        SettlementLineType.PENALTY_CAP_ADJUSTMENT,
                        capOffset,
                        String.format("Contractual Cap Adjustment: Penalties capped at %s (Waiver %s)",
                                config.monthlyPenaltyCap(), capOffset),
                        null
                ));
            }
        }

        return new PenaltyResult(effectivePenalty, lines);
    }

    public record PenaltyResult(Money totalPenalties, List<SignedLineItem> lines) {}
}
