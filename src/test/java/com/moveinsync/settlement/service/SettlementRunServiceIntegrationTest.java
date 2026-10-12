package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.PayslipResponse;
import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.SettlementRunResponse;
import com.moveinsync.settlement.entity.Settlement;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.entity.enums.SettlementStatus;
import com.moveinsync.settlement.entity.enums.TripSettlementStatus;
import com.moveinsync.settlement.repository.SettlementLineRepository;
import com.moveinsync.settlement.repository.SettlementRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.TripRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Service Integration: SettlementRunService Lifecycle & Idempotency Tests")
class SettlementRunServiceIntegrationTest {

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementLineRepository settlementLineRepository;

    @Autowired
    private com.moveinsync.settlement.repository.ReconciliationRepository reconciliationRepository;

    @Autowired
    private com.moveinsync.settlement.repository.AdjustmentRepository adjustmentRepository;

    @Autowired
    private com.moveinsync.settlement.repository.DisputeRepository disputeRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private com.moveinsync.settlement.repository.BusinessUnitRepository businessUnitRepository;

    @Autowired
    private com.moveinsync.settlement.repository.DriverRepository driverRepository;

    @Autowired
    private com.moveinsync.settlement.repository.VendorRepository vendorRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRIVER_1_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final String BILLING_PERIOD = "2026-10";

    @BeforeAll
    void initCleanState() {
        adjustmentRepository.deleteAll();
        disputeRepository.deleteAll();
        reconciliationRepository.deleteAll();
        settlementLineRepository.deleteAll();
        settlementRepository.deleteAll();
        settlementRunRepository.deleteAll();
        tripRepository.findAll().forEach(t -> {
            t.setSettlementStatus(TripSettlementStatus.PENDING);
            tripRepository.save(t);
        });
    }

    @Test
    @Order(1)
    @DisplayName("Executes full settlement run successfully with driver settlements and signed lines")
    void testExecuteSettlementRun() {
        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod(BILLING_PERIOD)
                .build();

        SettlementRunResponse response = settlementRunService.executeSettlementRun(request, "TEST_ADMIN");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.isIdempotentReplay()).isFalse();
        assertThat(response.getStatus()).isEqualTo(SettlementRunStatus.CALCULATED);
        assertThat(response.getDriversSettledCount()).isGreaterThan(0);
        assertThat(response.getTotalGrossBasePay()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getTotalNetPayout()).isGreaterThan(BigDecimal.ZERO);

        // Verify database persistence
        List<Settlement> settlements = settlementRepository.findBySettlementRunId(response.getId());
        assertThat(settlements).hasSize(response.getDriversSettledCount());
        for (Settlement settlement : settlements) {
            List<com.moveinsync.settlement.entity.SettlementLine> lines = settlementLineRepository.findBySettlementId(settlement.getId());
            assertThat(lines).isNotEmpty();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Run-twice safe: Repeating the identical settlement run replays existing results with zero duplicate records")
    void testIdempotentReplay() {
        long runCountBefore = settlementRunRepository.count();
        long settlementCountBefore = settlementRepository.count();

        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod(BILLING_PERIOD)
                .build();

        SettlementRunResponse replayedResponse = settlementRunService.executeSettlementRun(request, "TEST_ADMIN");

        assertThat(replayedResponse).isNotNull();
        assertThat(replayedResponse.isIdempotentReplay()).isTrue();

        // Exact record count preservation - no duplicates created
        assertThat(settlementRunRepository.count()).isEqualTo(runCountBefore);
        assertThat(settlementRepository.count()).isEqualTo(settlementCountBefore);
    }

    @Test
    @Order(3)
    @DisplayName("Retrieves itemized driver payslip with exact signed line reconciliation")
    void testGetDriverPayslip() {
        PayslipResponse payslip = settlementRunService.getDriverPayslip(DRIVER_1_ID, BILLING_PERIOD);

        assertThat(payslip).isNotNull();
        assertThat(payslip.getDriverId()).isEqualTo(DRIVER_1_ID);
        assertThat(payslip.getDriverCode()).isEqualTo("DRV-10042");
        assertThat(payslip.getBillingPeriod()).isEqualTo(BILLING_PERIOD);
        assertThat(payslip.getTripsCount()).isGreaterThan(0);
        assertThat(payslip.getSummary().getGrossBasePay()).isGreaterThan(BigDecimal.ZERO);
        assertThat(payslip.getSummary().getNetPayout()).isGreaterThan(BigDecimal.ZERO);

        // Verify mathematical invariant
        assertThat(payslip.getVerification().isReconciled()).isTrue();
        assertThat(payslip.getVerification().getLinesSum()).isEqualByComparingTo(payslip.getSummary().getNetPayout());
        assertThat(payslip.getLineItems()).isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("Approves settlement run and transitions statuses to FINALIZED / APPROVED")
    void testApproveSettlementRun() {
        SettlementRun run = settlementRunRepository
                .findByVendorIdAndBusinessUnitIdAndBillingPeriod(VENDOR_ID, BU_ID, BILLING_PERIOD)
                .orElseThrow();

        SettlementRunResponse approved = settlementRunService.approveSettlementRun(run.getId(), "TEST_APPROVER");

        assertThat(approved.getStatus()).isEqualTo(SettlementRunStatus.FINALIZED);

        // Verify all driver settlements transitioned to APPROVED
        List<Settlement> settlements = settlementRepository.findBySettlementRunId(run.getId());
        for (Settlement s : settlements) {
            assertThat(s.getStatus()).isEqualTo(SettlementStatus.APPROVED);
        }
    }

    @Test
    @Order(5)
    @DisplayName("Contract Scoping: Rejects settlement run if no active contract exists for target Business Unit")
    void testContractMissingForBusinessUnitThrowsException() {
        // Create an uncontracted Business Unit
        com.moveinsync.settlement.entity.BusinessUnit uncontractedBu = businessUnitRepository.save(
                com.moveinsync.settlement.entity.BusinessUnit.builder()
                        .id(UUID.randomUUID())
                        .unitCode("BU-MUM-" + UUID.randomUUID().toString().substring(0, 8))
                        .name("Mumbai Tech Hub")
                        .timezone("Asia/Kolkata")
                        .isActive(true)
                        .createdAt(java.time.Instant.now())
                        .build()
        );

        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(uncontractedBu.getId())
                .billingPeriod("2026-11")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> settlementRunService.executeSettlementRun(request, "TEST_ADMIN"))
                .isInstanceOf(com.moveinsync.settlement.exception.ResourceNotFoundException.class)
                .hasMessageContaining("No active contract found for vendor");
    }

    @Test
    @Order(6)
    @DisplayName("Delta Posting: Approved dispute adjustment enters the subsequent monthly payout cycle exactly once")
    void testApprovedAdjustmentConsumedIntoNextRun() {
        // Retrieve approved October settlement for driver 1
        Settlement octSettlement = settlementRepository.findByDriverIdAndBillingPeriodWithLines(DRIVER_1_ID, BILLING_PERIOD).orElseThrow();

        com.moveinsync.settlement.entity.Trip targetTrip = tripRepository.findAll().stream()
                .filter(t -> DRIVER_1_ID.equals(t.getDriver().getId()))
                .findFirst()
                .orElseThrow();

        // 1. Create dispute and approved adjustment
        com.moveinsync.settlement.entity.Dispute dispute = disputeRepository.save(
                com.moveinsync.settlement.entity.Dispute.builder()
                        .id(UUID.randomUUID())
                        .settlement(octSettlement)
                        .trip(targetTrip)
                        .reason("Underpayment on night shift tariff")
                        .claimedDelta(new BigDecimal("45.00"))
                        .status(com.moveinsync.settlement.entity.enums.DisputeStatus.APPROVED)
                        .resolvedBy("TEST_APPROVER")
                        .resolutionNotes("Verified night surcharge difference")
                        .createdAt(java.time.Instant.now())
                        .build()
        );

        com.moveinsync.settlement.entity.Adjustment adjustment = adjustmentRepository.save(
                com.moveinsync.settlement.entity.Adjustment.builder()
                        .id(UUID.randomUUID())
                        .dispute(dispute)
                        .settlement(octSettlement)
                        .adjustmentType(com.moveinsync.settlement.entity.enums.AdjustmentType.TRIP_RATE_CORRECTION)
                        .deltaAmount(new BigDecimal("45.00"))
                        .status(com.moveinsync.settlement.entity.enums.AdjustmentStatus.APPROVED)
                        .createdAt(java.time.Instant.now())
                        .build()
        );

        // 2. Seed an eligible trip in November 2026 for driver 1
        tripRepository.save(com.moveinsync.settlement.entity.Trip.builder()
                .id(UUID.randomUUID())
                .sourceTripId("TRIP-NOV-TEST-" + UUID.randomUUID())
                .driver(driverRepository.findById(DRIVER_1_ID).orElseThrow())
                .vendor(vendorRepository.findById(VENDOR_ID).orElseThrow())
                .businessUnit(businessUnitRepository.findById(BU_ID).orElseThrow())
                .vehicleCategory("SEDAN")
                .startedAt(java.time.Instant.parse("2026-11-05T08:00:00Z"))
                .completedAt(java.time.Instant.parse("2026-11-05T09:00:00Z"))
                .status(com.moveinsync.settlement.entity.enums.TripStatus.COMPLETED)
                .settlementStatus(TripSettlementStatus.PENDING)
                .delayMinutes(0)
                .createdAt(java.time.Instant.now())
                .build());

        // 3. Execute November 2026 settlement run
        SettlementRunRequest novRequest = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod("2026-11")
                .build();

        SettlementRunResponse novRun = settlementRunService.executeSettlementRun(novRequest, "TEST_ADMIN");
        assertThat(novRun).isNotNull();
        assertThat(novRun.getStatus()).isEqualTo(SettlementRunStatus.CALCULATED);

        // 4. Verify driver 1's November settlement contains the adjustment line item
        Settlement novSettlement = settlementRepository.findByDriverIdAndBillingPeriodWithLines(DRIVER_1_ID, "2026-11").orElseThrow();
        List<com.moveinsync.settlement.entity.SettlementLine> adjLines = novSettlement.getLines().stream()
                .filter(l -> l.getLineType() == com.moveinsync.settlement.entity.enums.SettlementLineType.ADJUSTMENT)
                .toList();

        assertThat(adjLines).hasSize(1);
        assertThat(adjLines.getFirst().getSignedAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(adjLines.getFirst().getSourceReferenceId()).isEqualTo(adjustment.getId().toString());

        // 5. Verify the adjustment record is now marked DISBURSED and linked to the November run
        com.moveinsync.settlement.entity.Adjustment reloadedAdj = adjustmentRepository.findById(adjustment.getId()).orElseThrow();
        assertThat(reloadedAdj.getStatus()).isEqualTo(com.moveinsync.settlement.entity.enums.AdjustmentStatus.DISBURSED);
        assertThat(reloadedAdj.getConsumedRun()).isNotNull();
        assertThat(reloadedAdj.getConsumedRun().getId()).isEqualTo(novRun.getId());
        assertThat(reloadedAdj.getConsumedSettlement()).isNotNull();
        assertThat(reloadedAdj.getConsumedSettlement().getId()).isEqualTo(novSettlement.getId());

        // 6. Verify mathematical invariant holds: sum(lines) == netPayout
        BigDecimal sumOfLines = novSettlement.getLines().stream()
                .map(com.moveinsync.settlement.entity.SettlementLine::getSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfLines).isEqualByComparingTo(novSettlement.getNetPayout());
    }

    @Test
    @Order(7)
    @DisplayName("Failure Recovery: Retrying a previously FAILED settlement run reuses the claim and succeeds")
    void testRetryFailedSettlementRunReusesRecordAndSucceeds() {
        // 1. Pre-seed a FAILED settlement run for December 2026
        SettlementRun failedRun = settlementRunRepository.save(
                SettlementRun.builder()
                        .id(UUID.randomUUID())
                        .vendor(vendorRepository.findById(VENDOR_ID).orElseThrow())
                        .businessUnit(businessUnitRepository.findById(BU_ID).orElseThrow())
                        .billingPeriod("2026-12")
                        .runNumber(1)
                        .status(SettlementRunStatus.FAILED)
                        .totalGross(BigDecimal.ZERO)
                        .totalIncentives(BigDecimal.ZERO)
                        .totalPenalties(BigDecimal.ZERO)
                        .totalTds(BigDecimal.ZERO)
                        .totalNetPayout(BigDecimal.ZERO)
                        .createdBy("PREVIOUS_CRASH")
                        .createdAt(java.time.Instant.now().minusSeconds(3600))
                        .build()
        );

        // 2. Seed an eligible trip in December 2026 for driver 1
        tripRepository.save(com.moveinsync.settlement.entity.Trip.builder()
                .id(UUID.randomUUID())
                .sourceTripId("TRIP-DEC-TEST-" + UUID.randomUUID())
                .driver(driverRepository.findById(DRIVER_1_ID).orElseThrow())
                .vendor(vendorRepository.findById(VENDOR_ID).orElseThrow())
                .businessUnit(businessUnitRepository.findById(BU_ID).orElseThrow())
                .vehicleCategory("SEDAN")
                .startedAt(java.time.Instant.parse("2026-12-05T08:00:00Z"))
                .completedAt(java.time.Instant.parse("2026-12-05T09:00:00Z"))
                .status(com.moveinsync.settlement.entity.enums.TripStatus.COMPLETED)
                .settlementStatus(TripSettlementStatus.PENDING)
                .delayMinutes(0)
                .createdAt(java.time.Instant.now())
                .build());

        // 3. Retry the December settlement run
        SettlementRunRequest decRequest = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod("2026-12")
                .build();

        SettlementRunResponse retriedRun = settlementRunService.executeSettlementRun(decRequest, "RETRY_ADMIN");

        assertThat(retriedRun).isNotNull();
        assertThat(retriedRun.getId()).isEqualTo(failedRun.getId());
        assertThat(retriedRun.getStatus()).isEqualTo(SettlementRunStatus.CALCULATED);
        assertThat(retriedRun.getRunNumber()).isEqualTo(2);
        assertThat(retriedRun.getDriversSettledCount()).isEqualTo(1);
    }
}
