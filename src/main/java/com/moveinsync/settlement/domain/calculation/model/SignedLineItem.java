package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.SettlementLineType;

public record SignedLineItem(
        SettlementLineType lineType,
        Money signedAmount,
        String description,
        String sourceReferenceId
) {}
