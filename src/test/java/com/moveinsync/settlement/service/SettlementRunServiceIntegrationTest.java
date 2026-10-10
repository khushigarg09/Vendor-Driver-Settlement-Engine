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
    private com.moveinsync.settlement.repository.VendorInvoiceRepository vendorInvoiceRepository;

    @Autowired
    private com.moveinsync.settlement.repository.AdjustmentRepository adjustmentRepository;

    @Autowired
    private com.moveinsync.settlement.repository.DisputeRepository disputeRepository;

    @Autowired
    private TripRepository tripRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRIVER_1_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final String BILLING_PERIOD = "2026-10";

    @BeforeAll
    void initCleanState() {
        adjustmentRepository.deleteAll();
        disputeRepository.deleteAll();
        reconciliationRepository.deleteAll();
        vendorInvoiceRepository.deleteAll();
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
}
