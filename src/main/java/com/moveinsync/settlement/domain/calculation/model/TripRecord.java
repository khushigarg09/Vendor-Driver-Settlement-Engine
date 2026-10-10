package com.moveinsync.settlement.domain.calculation.model;

import com.moveinsync.settlement.entity.enums.TripStatus;

import java.time.Instant;
import java.util.UUID;

public record TripRecord(
        UUID id,
        String sourceTripId,
        UUID driverId,
        String vehicleCategory,
        Instant startedAt,
        Instant completedAt,
        int delayMinutes,
        TripStatus status
) {
    public boolean isCompleted() {
        return status == TripStatus.COMPLETED;
    }

    public boolean isOnTime() {
        return delayMinutes <= 0;
    }
}
