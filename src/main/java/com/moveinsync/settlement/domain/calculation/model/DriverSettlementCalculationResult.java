package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;

import java.util.List;
import java.util.UUID;

public record DriverSettlementCalculationResult(
        UUID driverId,
        String billingPeriod,
        int tripsCount,
        Money grossBasePay,
        Money incentivesTotal,
        Money penaltiesTotal,
        Money tdsTaxTotal,
        Money netPayout,
        List<SignedLineItem> lineItems
) {}
