package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.ContractRate;
import com.moveinsync.settlement.entity.Driver;
import com.moveinsync.settlement.entity.Trip;
import com.moveinsync.settlement.entity.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Database: Flyway Migrations and Reference Data Integration Tests")
class FlywayMigrationIntegrationTest {

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VendorContractRepository contractRepository;

    @Autowired
    private ContractRateRepository contractRateRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private VendorInvoiceRepository invoiceRepository;

    @Test
    @DisplayName("Should successfully load seeded Vendor VND-BLR-01")
    void testSeededVendor() {
        Optional<Vendor> vendor = vendorRepository.findByVendorCode("VND-BLR-01");
        assertThat(vendor).isPresent();
        assertThat(vendor.get().getName()).isEqualTo("QuickCabs Logistics Private Limited");
        assertThat(vendor.get().getTaxIdentifier()).isEqualTo("AAACQ1234F");
    }

    @Test
    @DisplayName("Should load seeded Business Unit BU-BLR-TECH-PARK")
    void testSeededBusinessUnit() {
        assertThat(businessUnitRepository.findByUnitCode("BU-BLR-TECH-PARK")).isPresent();
    }

    @Test
    @DisplayName("Should load seeded Drivers for Vendor")
    void testSeededDrivers() {
        UUID vendorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<Driver> drivers = driverRepository.findByVendorId(vendorId);
        assertThat(drivers).hasSize(2);
        assertThat(drivers).extracting(Driver::getDriverCode)
                .containsExactlyInAnyOrder("DRV-10042", "DRV-10043");
    }

    @Test
    @DisplayName("Should load seeded Contract and 2 Effective-Dated Rates")
    void testSeededContractAndRates() {
        UUID contractId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        List<ContractRate> rates = contractRateRepository.findByContractId(contractId);
        assertThat(rates).hasSize(2);
    }

    @Test
    @DisplayName("Should load seeded Completed Trips")
    void testSeededTrips() {
        Optional<Trip> trip1 = tripRepository.findBySourceTripId("TRIP-2026-10-001");
        assertThat(trip1).isPresent();
        assertThat(trip1.get().getStatus().name()).isEqualTo("COMPLETED");

        Optional<Trip> trip2 = tripRepository.findBySourceTripId("TRIP-2026-10-002");
        assertThat(trip2).isPresent();
    }

    @Test
    @DisplayName("Should load seeded Violation and Vendor Invoice")
    void testSeededViolationAndInvoice() {
        assertThat(violationRepository.findBySourceViolationId("VIO-2026-10-099")).isPresent();

        UUID vendorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThat(invoiceRepository.findByVendorIdAndInvoiceNumber(vendorId, "INV-VND01-2026-10")).isPresent();
    }

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Should verify V4 migration and persist AuditLog entity")
    void testV4AuditLogsMigration() {
        var log = com.moveinsync.settlement.entity.AuditLog.builder()
                .id(UUID.randomUUID())
                .action("SCHEMA_VERIFY")
                .entityType("SYSTEM")
                .entityId("MIGRATION_V4")
                .performedBy("TEST_RUNNER")
                .performedRole("ADMIN")
                .detailsJson("{\"status\":\"ok\"}")
                .createdAt(java.time.Instant.now())
                .build();

        auditLogRepository.save(log);
        assertThat(auditLogRepository.findById(log.getId())).isPresent();
    }
}
