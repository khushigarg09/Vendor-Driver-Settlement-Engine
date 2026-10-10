package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.entity.enums.SettlementStatus;
import com.moveinsync.settlement.entity.enums.TripStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Database: Schema Constraints and Idempotency Integrity Integration Tests")
class SchemaConstraintsIntegrationTest {

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private DriverRepository driverRepository;

    private final UUID vendorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID businessUnitId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID driverId = UUID.fromString("44444444-4444-4444-4444-444444444441");

    @Test
    @DisplayName("Duplicate settlement run for same vendor, BU, and period must throw DataIntegrityViolationException")
    void testSettlementRunIdempotencyConstraint() {
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow();
        BusinessUnit bu = businessUnitRepository.findById(businessUnitId).orElseThrow();

        SettlementRun run1 = SettlementRun.builder()
                .id(UUID.randomUUID())
                .vendor(vendor)
                .businessUnit(bu)
                .billingPeriod("2026-10")
                .status(SettlementRunStatus.FINALIZED)
                .createdBy("system")
                .totalGross(BigDecimal.ZERO)
                .totalNetPayout(BigDecimal.ZERO)
                .build();
        settlementRunRepository.saveAndFlush(run1);

        SettlementRun run2 = SettlementRun.builder()
                .id(UUID.randomUUID())
                .vendor(vendor)
                .businessUnit(bu)
                .billingPeriod("2026-10") // Duplicate scope!
                .status(SettlementRunStatus.STARTED)
                .createdBy("system")
                .totalGross(BigDecimal.ZERO)
                .totalNetPayout(BigDecimal.ZERO)
                .build();

        assertThatThrownBy(() -> settlementRunRepository.saveAndFlush(run2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Duplicate source_trip_id must throw DataIntegrityViolationException")
    void testTripDeduplicationConstraint() {
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow();
        BusinessUnit bu = businessUnitRepository.findById(businessUnitId).orElseThrow();
        Driver driver = driverRepository.findById(driverId).orElseThrow();

        Trip duplicateTrip = Trip.builder()
                .id(UUID.randomUUID())
                .sourceTripId("TRIP-2026-10-001") // Already seeded in V3!
                .vendor(vendor)
                .businessUnit(bu)
                .driver(driver)
                .vehicleCategory("SEDAN")
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .status(TripStatus.COMPLETED)
                .build();

        assertThatThrownBy(() -> tripRepository.saveAndFlush(duplicateTrip))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Duplicate driver settlement within same run must throw DataIntegrityViolationException")
    void testDriverSettlementUniquenessConstraint() {
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow();
        BusinessUnit bu = businessUnitRepository.findById(businessUnitId).orElseThrow();
        Driver driver = driverRepository.findById(driverId).orElseThrow();

        SettlementRun run = SettlementRun.builder()
                .id(UUID.randomUUID())
                .vendor(vendor)
                .businessUnit(bu)
                .billingPeriod("2026-11")
                .status(SettlementRunStatus.STARTED)
                .createdBy("admin")
                .build();
        settlementRunRepository.saveAndFlush(run);

        Settlement s1 = Settlement.builder()
                .id(UUID.randomUUID())
                .settlementRun(run)
                .driver(driver)
                .billingPeriod("2026-11")
                .grossBasePay(new BigDecimal("1000.00"))
                .incentivesTotal(BigDecimal.ZERO)
                .penaltiesTotal(BigDecimal.ZERO)
                .tdsTaxTotal(BigDecimal.ZERO)
                .netPayout(new BigDecimal("1000.00"))
                .status(SettlementStatus.CALCULATED)
                .build();
        settlementRepository.saveAndFlush(s1);

        Settlement s2 = Settlement.builder()
                .id(UUID.randomUUID())
                .settlementRun(run)
                .driver(driver) // Duplicate driver for same run!
                .billingPeriod("2026-11")
                .grossBasePay(new BigDecimal("1000.00"))
                .incentivesTotal(BigDecimal.ZERO)
                .penaltiesTotal(BigDecimal.ZERO)
                .tdsTaxTotal(BigDecimal.ZERO)
                .netPayout(new BigDecimal("1000.00"))
                .status(SettlementStatus.CALCULATED)
                .build();

        assertThatThrownBy(() -> settlementRepository.saveAndFlush(s2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
