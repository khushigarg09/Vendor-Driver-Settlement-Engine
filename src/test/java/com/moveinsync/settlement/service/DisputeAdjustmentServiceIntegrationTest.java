package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.AdjustmentResponse;
import com.moveinsync.settlement.dto.DisputeRequest;
import com.moveinsync.settlement.dto.DisputeResolutionRequest;
import com.moveinsync.settlement.dto.DisputeResolutionResponse;
import com.moveinsync.settlement.dto.DisputeResponse;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.entity.enums.*;
import com.moveinsync.settlement.exception.ConflictException;
import com.moveinsync.settlement.exception.SettlementDomainException;
import com.moveinsync.settlement.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Service Integration: DisputeAdjustmentService Immutability & Delta Workflow Tests")
class DisputeAdjustmentServiceIntegrationTest {

    @Autowired
    private DisputeAdjustmentService disputeAdjustmentService;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private AdjustmentRepository adjustmentRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRIVER_1_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID DRIVER_2_ID = UUID.fromString("44444444-4444-4444-4444-444444444442");
    private static final UUID TRIP_1_ID = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID TRIP_2_ID = UUID.fromString("66666666-6666-6666-6666-666666666662");
    private static final UUID VIOLATION_ID = UUID.fromString("77777777-7777-7777-7777-777777777771");

    private static UUID approvedSettlementId;
    private static UUID draftSettlementId;

    private Settlement approvedSettlement;
    private Settlement draftSettlement;

    @BeforeEach
    void setUp() {
        if (approvedSettlementId == null) {
            Vendor vendor = vendorRepository.findById(VENDOR_ID).orElseThrow();
            BusinessUnit bu = businessUnitRepository.findById(BU_ID).orElseThrow();
            Driver driver1 = driverRepository.findById(DRIVER_1_ID).orElseThrow();
            Driver driver2 = driverRepository.findById(DRIVER_2_ID).orElseThrow();

            SettlementRun run1 = settlementRunRepository.save(SettlementRun.builder()
                    .id(UUID.randomUUID())
                    .vendor(vendor)
                    .businessUnit(bu)
                    .billingPeriod("2026-11")
                    .status(SettlementRunStatus.FINALIZED)
                    .totalGross(new BigDecimal("2500.00"))
                    .totalIncentives(BigDecimal.ZERO)
                    .totalPenalties(BigDecimal.ZERO)
                    .totalTds(BigDecimal.ZERO)
                    .totalNetPayout(new BigDecimal("2500.00"))
                    .createdBy("TEST_DISPUTE_SETUP")
                    .createdAt(Instant.now())
                    .build());

            Settlement s1 = settlementRepository.save(Settlement.builder()
                    .id(UUID.randomUUID())
                    .settlementRun(run1)
                    .driver(driver1)
                    .billingPeriod("2026-11")
                    .tripsCount(2)
                    .grossBasePay(new BigDecimal("2500.00"))
                    .incentivesTotal(BigDecimal.ZERO)
                    .penaltiesTotal(BigDecimal.ZERO)
                    .tdsTaxTotal(BigDecimal.ZERO)
                    .netPayout(new BigDecimal("2500.00"))
                    .status(SettlementStatus.APPROVED)
                    .createdAt(Instant.now())
                    .build());
            approvedSettlementId = s1.getId();

            SettlementRun run2 = settlementRunRepository.save(SettlementRun.builder()
                    .id(UUID.randomUUID())
                    .vendor(vendor)
                    .businessUnit(bu)
                    .billingPeriod("2026-12")
                    .status(SettlementRunStatus.STARTED)
                    .totalGross(BigDecimal.ZERO)
                    .totalIncentives(BigDecimal.ZERO)
                    .totalPenalties(BigDecimal.ZERO)
                    .totalTds(BigDecimal.ZERO)
                    .totalNetPayout(BigDecimal.ZERO)
                    .createdBy("TEST_STARTED")
                    .createdAt(Instant.now())
                    .build());

            Settlement s2 = settlementRepository.save(Settlement.builder()
                    .id(UUID.randomUUID())
                    .settlementRun(run2)
                    .driver(driver2)
                    .billingPeriod("2026-12")
                    .tripsCount(1)
                    .grossBasePay(new BigDecimal("500.00"))
                    .incentivesTotal(BigDecimal.ZERO)
                    .penaltiesTotal(BigDecimal.ZERO)
                    .tdsTaxTotal(BigDecimal.ZERO)
                    .netPayout(new BigDecimal("500.00"))
                    .status(SettlementStatus.CALCULATED)
                    .createdAt(Instant.now())
                    .build());
            draftSettlementId = s2.getId();
        }

        approvedSettlement = settlementRepository.findById(approvedSettlementId).orElseThrow();
        draftSettlement = settlementRepository.findById(draftSettlementId).orElseThrow();
    }

    @Test
    @Order(1)
    @DisplayName("Files trip dispute successfully and flags trip status as DISPUTED")
    void testFileTripDispute() {
        DisputeRequest request = DisputeRequest.builder()
                .settlementId(approvedSettlement.getId())
                .disputedTripId(TRIP_1_ID)
                .reason("Mid-month rate revision discrepancy for trip on Oct 10th.")
                .claimedDelta(new BigDecimal("30.00"))
                .build();

        DisputeResponse response = disputeAdjustmentService.fileDispute(request, "DRIVER_10042");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(response.getClaimedDelta()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(response.getTripId()).isEqualTo(TRIP_1_ID);

        Trip trip = tripRepository.findById(TRIP_1_ID).orElseThrow();
        assertThat(trip.getSettlementStatus()).isEqualTo(TripSettlementStatus.DISPUTED);
    }

    @Test
    @Order(2)
    @DisplayName("Rejects filing duplicate open dispute on the same trip with 409 Conflict")
    void testFileDuplicateTripDisputeConflict() {
        DisputeRequest request = DisputeRequest.builder()
                .settlementId(approvedSettlement.getId())
                .disputedTripId(TRIP_1_ID)
                .reason("Duplicate attempt for the same disputed trip.")
                .claimedDelta(new BigDecimal("30.00"))
                .build();

        assertThatThrownBy(() -> disputeAdjustmentService.fileDispute(request, "DRIVER_10042"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("An active dispute is already open");
    }

    @Test
    @Order(3)
    @DisplayName("Files violation penalty dispute successfully")
    void testFileViolationDispute() {
        DisputeRequest request = DisputeRequest.builder()
                .settlementId(approvedSettlement.getId())
                .disputedViolationId(VIOLATION_ID)
                .reason("Client delayed pickup, over-speed was reported in error.")
                .claimedDelta(new BigDecimal("500.00"))
                .build();

        DisputeResponse response = disputeAdjustmentService.fileDispute(request, "DRIVER_10042");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(response.getViolationId()).isEqualTo(VIOLATION_ID);
        assertThat(response.getClaimedDelta()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @Order(4)
    @DisplayName("Rejects dispute against settlement whose run is still in STARTED status")
    void testFileDisputeUnfinalizedRunSettlementThrowsException() {
        DisputeRequest request = DisputeRequest.builder()
                .settlementId(draftSettlement.getId())
                .disputedTripId(TRIP_2_ID)
                .reason("Dispute on started run should fail.")
                .claimedDelta(new BigDecimal("50.00"))
                .build();

        assertThatThrownBy(() -> disputeAdjustmentService.fileDispute(request, "DRIVER_10042"))
                .isInstanceOf(SettlementDomainException.class)
                .hasMessageContaining("whose run is in STARTED status");
    }

    @Test
    @Order(5)
    @DisplayName("Dispute Approval: Generates isolated Adjustment and preserves Settlement Immutability")
    void testApproveTripDisputeCreatesAdjustmentAndPreservesSettlementImmutability() {
        // Find the open trip dispute filed in test 1
        List<Dispute> openDisputes = disputeRepository.findBySettlementId(approvedSettlement.getId()).stream()
                .filter(d -> d.getStatus() == DisputeStatus.OPEN && d.getTrip() != null)
                .toList();
        assertThat(openDisputes).isNotEmpty();
        Dispute openDispute = openDisputes.getFirst();

        BigDecimal originalNetPayout = approvedSettlement.getNetPayout();
        BigDecimal originalGrossBasePay = approvedSettlement.getGrossBasePay();

        DisputeResolutionRequest resolveReq = DisputeResolutionRequest.builder()
                .approvedDelta(new BigDecimal("30.00"))
                .notes("Verified rate differential from contract rate revision.")
                .build();

        DisputeResolutionResponse resolution = disputeAdjustmentService
                .approveDispute(openDispute.getId(), resolveReq, "FINANCE_APPROVER");

        assertThat(resolution).isNotNull();
        assertThat(resolution.getStatus()).isEqualTo(DisputeStatus.APPROVED);
        assertThat(resolution.getAdjustment()).isNotNull();

        AdjustmentResponse adjustment = resolution.getAdjustment();
        assertThat(adjustment.getDeltaAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(adjustment.getAdjustmentType()).isEqualTo(AdjustmentType.TRIP_RATE_CORRECTION);
        assertThat(adjustment.getStatus()).isEqualTo(AdjustmentStatus.APPROVED);
        assertThat(adjustment.getLines()).hasSize(1);
        assertThat(adjustment.getLines().getFirst().getSignedAmount()).isEqualByComparingTo(new BigDecimal("30.00"));

        // CRITICAL INVARIANT: Settlement entity in database is 100% IMMUTABLE
        Settlement reloadedSettlement = settlementRepository.findById(approvedSettlement.getId()).orElseThrow();
        assertThat(reloadedSettlement.getNetPayout()).isEqualByComparingTo(originalNetPayout);
        assertThat(reloadedSettlement.getGrossBasePay()).isEqualByComparingTo(originalGrossBasePay);

        // Disputed trip status is restored to SETTLED
        Trip trip = tripRepository.findById(TRIP_1_ID).orElseThrow();
        assertThat(trip.getSettlementStatus()).isEqualTo(TripSettlementStatus.SETTLED);
    }

    @Test
    @Order(6)
    @DisplayName("Dispute Rejection: Updates status to REJECTED with zero adjustments created")
    void testRejectViolationDispute() {
        List<Dispute> openDisputes = disputeRepository.findBySettlementId(approvedSettlement.getId()).stream()
                .filter(d -> d.getStatus() == DisputeStatus.OPEN && d.getViolation() != null)
                .toList();
        assertThat(openDisputes).isNotEmpty();
        Dispute openDispute = openDisputes.getFirst();

        long adjustmentCountBefore = adjustmentRepository.count();

        DisputeResolutionRequest rejectReq = DisputeResolutionRequest.builder()
                .notes("Telemetry logs confirm driver exceeded 80 km/h limit; violation stands.")
                .build();

        DisputeResponse rejected = disputeAdjustmentService
                .rejectDispute(openDispute.getId(), rejectReq, "FINANCE_APPROVER");

        assertThat(rejected.getStatus()).isEqualTo(DisputeStatus.REJECTED);
        assertThat(rejected.getResolutionNotes()).contains("Telemetry logs confirm");

        // Zero adjustments generated
        assertThat(adjustmentRepository.count()).isEqualTo(adjustmentCountBefore);
    }

    @Test
    @Order(7)
    @DisplayName("Rejects re-approving or re-rejecting an already resolved dispute")
    void testCannotResolveAlreadyResolvedDispute() {
        List<Dispute> approvedList = disputeRepository.findBySettlementId(approvedSettlement.getId()).stream()
                .filter(d -> d.getStatus() == DisputeStatus.APPROVED)
                .toList();
        assertThat(approvedList).isNotEmpty();
        UUID approvedId = approvedList.getFirst().getId();

        assertThatThrownBy(() -> disputeAdjustmentService.approveDispute(approvedId, null, "ADMIN"))
                .isInstanceOf(SettlementDomainException.class)
                .hasMessageContaining("cannot be approved because it is in APPROVED state");
    }

    @Test
    @Order(8)
    @DisplayName("Lists disputes and adjustments filtered by settlement ID")
    void testListDisputesAndAdjustments() {
        List<DisputeResponse> disputes = disputeAdjustmentService.listDisputes(approvedSettlement.getId());
        assertThat(disputes).isNotEmpty();
        assertThat(disputes).allMatch(d -> d.getSettlementId().equals(approvedSettlement.getId()));

        List<AdjustmentResponse> adjustments = disputeAdjustmentService.listAdjustments(approvedSettlement.getId());
        assertThat(adjustments).isNotEmpty();
        assertThat(adjustments).allMatch(a -> a.getSettlementId().equals(approvedSettlement.getId()));
    }
}
