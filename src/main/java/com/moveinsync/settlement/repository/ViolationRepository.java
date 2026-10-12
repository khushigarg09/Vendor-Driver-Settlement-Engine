package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.Violation;
import com.moveinsync.settlement.entity.enums.ViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, UUID> {

    Optional<Violation> findBySourceViolationId(String sourceViolationId);

    @Query("""
        SELECT v FROM Violation v
        WHERE v.driver.id = :driverId
          AND v.status = :status
          AND v.occurredAt >= :startDate
          AND v.occurredAt < :endDate
        ORDER BY v.occurredAt ASC
    """)
    List<Violation> findPendingViolationsForDriverInWindow(
            @Param("driverId") UUID driverId,
            @Param("status") ViolationStatus status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        SELECT v FROM Violation v
        WHERE v.driver.id IN :driverIds
          AND v.status = :status
          AND v.occurredAt >= :startDate
          AND v.occurredAt < :endDate
        ORDER BY v.occurredAt ASC
    """)
    List<Violation> findPendingViolationsForDriversInWindow(
            @Param("driverIds") List<UUID> driverIds,
            @Param("status") ViolationStatus status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
