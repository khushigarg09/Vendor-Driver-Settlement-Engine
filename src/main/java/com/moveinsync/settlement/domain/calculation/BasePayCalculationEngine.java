package com.moveinsync.settlement.domain.calculation;

import com.moveinsync.settlement.domain.calculation.model.ContractRateRecord;
import com.moveinsync.settlement.domain.calculation.model.SignedLineItem;
import com.moveinsync.settlement.domain.calculation.model.TripRecord;
import com.moveinsync.settlement.domain.model.Money;
import com.moveinsync.settlement.entity.enums.BillingType;
import com.moveinsync.settlement.entity.enums.SettlementLineType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class BasePayCalculationEngine {

    private final RateResolver rateResolver;

    public BasePayCalculationEngine(RateResolver rateResolver) {
        this.rateResolver = Objects.requireNonNull(rateResolver, "RateResolver cannot be null");
    }

    public BasePayResult calculateBasePay(
            List<TripRecord> trips,
            List<ContractRateRecord> contractRates,
            BillingType billingType,
            ZoneId businessZoneId
    ) {
        Objects.requireNonNull(trips, "Trips cannot be null");
        Objects.requireNonNull(contractRates, "Contract rates cannot be null");
        Objects.requireNonNull(billingType, "Billing type cannot be null");

        ZoneId zone = (businessZoneId != null) ? businessZoneId : ZoneId.of("Asia/Kolkata");

        List<SignedLineItem> lines = new ArrayList<>();
        Money totalBase = Money.ZERO;

        List<TripRecord> eligibleTrips = trips.stream()
                .filter(TripRecord::isCompleted)
                .sorted(Comparator.comparing(TripRecord::completedAt))
                .toList();

        if (billingType == BillingType.PER_TRIP) {
            for (TripRecord trip : eligibleTrips) {
                Money tripRate = rateResolver.resolveRate(contractRates, trip.vehicleCategory(), trip.completedAt());
                totalBase = totalBase.add(tripRate);
                lines.add(new SignedLineItem(
                        SettlementLineType.BASE_PAY,
                        tripRate,
                        String.format("Base Pay: Trip %s (%s)", trip.sourceTripId(), trip.vehicleCategory()),
                        trip.sourceTripId()
                ));
            }
        } else if (billingType == BillingType.PER_DUTY) {
            // Group by distinct duty date in business timezone
            Map<LocalDate, List<TripRecord>> dutyDays = new TreeMap<>();
            for (TripRecord trip : eligibleTrips) {
                LocalDate date = trip.completedAt().atZone(zone).toLocalDate();
                dutyDays.computeIfAbsent(date, k -> new ArrayList<>()).add(trip);
            }

            for (Map.Entry<LocalDate, List<TripRecord>> entry : dutyDays.entrySet()) {
                LocalDate dutyDate = entry.getKey();
                TripRecord firstTrip = entry.getValue().get(0);
                Money dutyRate = rateResolver.resolveRate(contractRates, firstTrip.vehicleCategory(), firstTrip.completedAt());
                totalBase = totalBase.add(dutyRate);
                lines.add(new SignedLineItem(
                        SettlementLineType.BASE_PAY,
                        dutyRate,
                        String.format("Base Pay: Duty on %s (%d trips)", dutyDate, entry.getValue().size()),
                        dutyDate.toString()
                ));
            }
        }

        return new BasePayResult(totalBase, lines, eligibleTrips.size());
    }

    public record BasePayResult(Money totalBasePay, List<SignedLineItem> lines, int eligibleTripCount) {}
}
