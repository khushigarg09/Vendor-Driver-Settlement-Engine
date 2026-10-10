package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.*;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.BillingType;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SettlementCalculationEngine {

    private final BasePayCalculationEngine basePayEngine;
    private final IncentiveCalculationEngine incentiveEngine;
    private final PenaltyCalculationEngine penaltyEngine;
    private final CumulativeTdsCalculationEngine tdsEngine;
    private final NetPayslipReconciler netPayslipReconciler;

    public SettlementCalculationEngine(
            BasePayCalculationEngine basePayEngine,
            IncentiveCalculationEngine incentiveEngine,
            PenaltyCalculationEngine penaltyEngine,
            CumulativeTdsCalculationEngine tdsEngine,
            NetPayslipReconciler netPayslipReconciler
    ) {
        this.basePayEngine = Objects.requireNonNull(basePayEngine, "BasePayEngine cannot be null");
        this.incentiveEngine = Objects.requireNonNull(incentiveEngine, "IncentiveEngine cannot be null");
        this.penaltyEngine = Objects.requireNonNull(penaltyEngine, "PenaltyEngine cannot be null");
        this.tdsEngine = Objects.requireNonNull(tdsEngine, "CumulativeTdsEngine cannot be null");
        this.netPayslipReconciler = Objects.requireNonNull(netPayslipReconciler, "NetPayslipReconciler cannot be null");
    }

    public static SettlementCalculationEngine createDefault() {
        RateResolver rateResolver = new RateResolver();
        return new SettlementCalculationEngine(
                new BasePayCalculationEngine(rateResolver),
                new IncentiveCalculationEngine(),
                new PenaltyCalculationEngine(),
                new CumulativeTdsCalculationEngine(),
                new NetPayslipReconciler()
        );
    }

    public DriverSettlementCalculationResult computeDriverSettlement(
            UUID driverId,
            String billingPeriod,
            List<TripRecord> trips,
            List<ContractRateRecord> contractRates,
            List<ViolationRecord> violations,
            BillingType billingType,
            IncentiveConfig incentiveConfig,
            PenaltyConfig penaltyConfig,
            TdsConfig tdsConfig,
            Money previouslyWithheldTds,
            boolean perfectAttendance,
            ZoneId businessZoneId
    ) {
        Objects.requireNonNull(driverId, "Driver ID cannot be null");
        Objects.requireNonNull(billingPeriod, "Billing period cannot be null");

        IncentiveConfig incConfig = (incentiveConfig != null) ? incentiveConfig : IncentiveConfig.defaultConfig();
        PenaltyConfig penConfig = (penaltyConfig != null) ? penaltyConfig : PenaltyConfig.defaultConfig();
        TdsConfig tConfig = (tdsConfig != null) ? tdsConfig : TdsConfig.defaultConfig();

        List<SignedLineItem> allLines = new ArrayList<>();

        // 1. Calculate Base Pay
        BasePayCalculationEngine.BasePayResult baseResult = basePayEngine.calculateBasePay(
                trips, contractRates, billingType, businessZoneId
        );
        Money grossBase = baseResult.totalBasePay();
        allLines.addAll(baseResult.lines());

        // 2. Calculate Incentives
        IncentiveCalculationEngine.IncentiveResult incResult = incentiveEngine.calculateIncentives(
                trips, incConfig, perfectAttendance, businessZoneId
        );
        Money totalIncentives = incResult.totalIncentives();
        allLines.addAll(incResult.lines());

        // 3. Calculate Penalties
        PenaltyCalculationEngine.PenaltyResult penResult = penaltyEngine.calculatePenalties(
                violations, penConfig
        );
        Money totalPenalties = penResult.totalPenalties();
        allLines.addAll(penResult.lines());

        // 4. Calculate Cumulative TDS on Gross Earnings (Base + Incentives)
        Money grossEarnings = grossBase.add(totalIncentives);
        CumulativeTdsCalculationEngine.TdsResult tdsResult = tdsEngine.calculateTds(
                grossEarnings, previouslyWithheldTds, tConfig
        );
        Money tdsTaxTotal = tdsResult.tdsWithheld();
        allLines.addAll(tdsResult.lines());

        // 5. Compute Net Payout: Base + Incentives - Penalties - TDS
        Money netPayout = grossBase
                .add(totalIncentives)
                .subtract(totalPenalties)
                .subtract(tdsTaxTotal);

        // 6. Enforce Mathematical Invariant: sum(signedLines) == netPayout
        netPayslipReconciler.reconcileOrThrow(allLines, netPayout);

        return new DriverSettlementCalculationResult(
                driverId,
                billingPeriod,
                baseResult.eligibleTripCount(),
                grossBase,
                totalIncentives,
                totalPenalties,
                tdsTaxTotal,
                netPayout,
                allLines
        );
    }
}
