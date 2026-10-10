package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Trip;
import com.moveinsync.settlement.entity.enums.TripSettlementStatus;
import com.moveinsync.settlement.entity.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findBySourceTripId(String sourceTripId);

    @Query("""
        SELECT t FROM Trip t
        WHERE t.driver.id = :driverId
          AND t.status = :status
          AND t.completedAt >= :startDate
          AND t.completedAt < :endDate
        ORDER BY t.completedAt ASC
    """)
    List<Trip> findCompletedTripsForDriverInWindow(
            @Param("driverId") UUID driverId,
            @Param("status") TripStatus status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        SELECT t FROM Trip t
        WHERE t.vendor.id = :vendorId
          AND t.businessUnit.id = :businessUnitId
          AND t.status = :status
          AND t.settlementStatus = :settlementStatus
          AND t.completedAt >= :startDate
          AND t.completedAt < :endDate
    """)
    List<Trip> findEligibleTripsForSettlement(
            @Param("vendorId") UUID vendorId,
            @Param("businessUnitId") UUID businessUnitId,
            @Param("status") TripStatus status,
            @Param("settlementStatus") TripSettlementStatus settlementStatus,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
