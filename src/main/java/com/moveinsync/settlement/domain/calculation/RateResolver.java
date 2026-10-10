package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.ContractRateRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.exception.MissingContractRateException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RateResolver {

    public Money resolveRate(List<ContractRateRecord> availableRates, String vehicleCategory, Instant eventTimestamp) {
        Objects.requireNonNull(availableRates, "Available rates cannot be null");
        Objects.requireNonNull(vehicleCategory, "Vehicle category cannot be null");
        Objects.requireNonNull(eventTimestamp, "Event timestamp cannot be null");

        return availableRates.stream()
                .filter(rate -> rate.isApplicableAt(eventTimestamp, vehicleCategory))
                .max(Comparator.comparing(ContractRateRecord::effectiveFrom))
                .map(ContractRateRecord::rateAmount)
                .orElseThrow(() -> new MissingContractRateException(
                        String.format("No applicable contract rate found for vehicle category '%s' at timestamp '%s'",
                                vehicleCategory, eventTimestamp)
                ));
    }
}
