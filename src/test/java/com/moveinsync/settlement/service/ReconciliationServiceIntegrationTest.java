package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.ReconciliationResponse;
import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import com.moveinsync.settlement.dto.VendorInvoiceResponse;
import com.moveinsync.settlement.entity.BusinessUnit;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.entity.enums.ReconciliationStatus;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.exception.ConflictException;
import com.moveinsync.settlement.exception.SettlementDomainException;
import com.moveinsync.settlement.repository.BusinessUnitRepository;
import com.moveinsync.settlement.repository.ReconciliationRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.VendorInvoiceRepository;
import com.moveinsync.settlement.repository.VendorRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Service Integration: ReconciliationService Math, Invariants & Idempotency Tests")
class ReconciliationServiceIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    @Autowired
    private ReconciliationRepository reconciliationRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private Vendor vendor;
    private BusinessUnit businessUnit;

    @BeforeEach
    void setUp() {
        vendor = vendorRepository.findById(VENDOR_ID).orElseThrow();
        businessUnit = businessUnitRepository.findById(BU_ID).orElseThrow();
    }

    private SettlementRun createTestRun(BigDecimal netPayout, String period, Vendor v) {
        SettlementRun run = SettlementRun.builder()
                .id(UUID.randomUUID())
                .vendor(v != null ? v : vendor)
                .businessUnit(businessUnit)
                .billingPeriod(period)
                .status(SettlementRunStatus.CALCULATED)
                .totalGross(netPayout)
                .totalIncentives(BigDecimal.ZERO)
                .totalPenalties(BigDecimal.ZERO)
                .totalTds(BigDecimal.ZERO)
                .totalNetPayout(netPayout)
                .createdBy("TEST_SUITE")
                .createdAt(Instant.now())
                .build();
        return settlementRunRepository.save(run);
    }

    @Test
    @Order(1)
    @DisplayName("Submits external invoice successfully and retrieves it by ID")
    void testSubmitInvoice() {
        VendorInvoiceRequest request = VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-TEST-001")
                .billingPeriod("2026-01")
                .claimedAmount(new BigDecimal("15000.00"))
                .invoiceDate(LocalDate.of(2026, 1, 31))
                .build();

        VendorInvoiceResponse response = reconciliationService.submitInvoice(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-TEST-001");
        assertThat(response.getStatus()).isEqualTo("SUBMITTED");
        assertThat(response.getClaimedAmount()).isEqualByComparingTo(new BigDecimal("15000.00"));

        VendorInvoiceResponse retrieved = reconciliationService.getInvoiceById(response.getId());
        assertThat(retrieved.getInvoiceNumber()).isEqualTo("INV-TEST-001");
    }

    @Test
    @Order(2)
    @DisplayName("Rejects duplicate invoice number for the same vendor with 409 Conflict")
    void testSubmitDuplicateInvoiceConflict() {
        VendorInvoiceRequest request = VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-TEST-001")
                .billingPeriod("2026-01")
                .claimedAmount(new BigDecimal("15000.00"))
                .invoiceDate(LocalDate.of(2026, 1, 31))
                .build();

        assertThatThrownBy(() -> reconciliationService.submitInvoice(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("INV-TEST-001");
    }

    @Test
    @Order(3)
    @DisplayName("Exact Match: System net payout equals invoice claimed amount (variance == 0.00)")
    void testReconcileExactMatch() {
        SettlementRun run = createTestRun(new BigDecimal("10000.00"), "2026-02", vendor);

        VendorInvoiceResponse invoice = reconciliationService.submitInvoice(VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-EXACT-001")
                .billingPeriod("2026-02")
                .claimedAmount(new BigDecimal("10000.00"))
                .invoiceDate(LocalDate.of(2026, 2, 28))
                .build());

        ReconciliationRequest reconReq = ReconciliationRequest.builder()
                .settlementRunId(run.getId())
                .vendorInvoiceId(invoice.getId())
                .tolerance(new BigDecimal("100.00"))
                .build();

        ReconciliationResponse response = reconciliationService.reconcile(reconReq, "ADMIN_RECON");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReconciliationStatus.EXACT_MATCH);
        assertThat(response.getVarianceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getSystemAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(response.getInvoiceAmount()).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    @Order(4)
    @DisplayName("Matched within tolerance: Variance of ₹45.00 is <= tolerance ₹100.00")
    void testReconcileWithinTolerance() {
        SettlementRun run = createTestRun(new BigDecimal("20000.00"), "2026-03", vendor);

        VendorInvoiceResponse invoice = reconciliationService.submitInvoice(VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-TOL-001")
                .billingPeriod("2026-03")
                .claimedAmount(new BigDecimal("20045.00"))
                .invoiceDate(LocalDate.of(2026, 3, 31))
                .build());

        ReconciliationRequest reconReq = ReconciliationRequest.builder()
                .settlementRunId(run.getId())
                .vendorInvoiceId(invoice.getId())
                .tolerance(new BigDecimal("100.00"))
                .build();

        ReconciliationResponse response = reconciliationService.reconcile(reconReq, "ADMIN_RECON");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReconciliationStatus.MATCHED_WITHIN_TOLERANCE);
        assertThat(response.getVarianceAmount()).isEqualByComparingTo(new BigDecimal("45.00"));
    }

    @Test
    @Order(5)
    @DisplayName("Discrepancy flagged: Variance of ₹850.00 exceeds tolerance ₹100.00")
    void testReconcileDiscrepancyFlagged() {
        SettlementRun run = createTestRun(new BigDecimal("50000.00"), "2026-04", vendor);

        VendorInvoiceResponse invoice = reconciliationService.submitInvoice(VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-DISC-001")
                .billingPeriod("2026-04")
                .claimedAmount(new BigDecimal("50850.00"))
                .invoiceDate(LocalDate.of(2026, 4, 30))
                .build());

        ReconciliationRequest reconReq = ReconciliationRequest.builder()
                .settlementRunId(run.getId())
                .vendorInvoiceId(invoice.getId())
                .tolerance(new BigDecimal("100.00"))
                .build();

        ReconciliationResponse response = reconciliationService.reconcile(reconReq, "ADMIN_RECON");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ReconciliationStatus.DISCREPANCY_FLAGGED);
        assertThat(response.getVarianceAmount()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(response.getExplanation()).contains("exceeds tolerance ceiling");
    }

    @Test
    @Order(6)
    @DisplayName("Reconciliation Idempotency: Re-reconciling same run and invoice returns existing record")
    void testReconcileIdempotency() {
        SettlementRun run = createTestRun(new BigDecimal("30000.00"), "2026-05", vendor);

        VendorInvoiceResponse invoice = reconciliationService.submitInvoice(VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-IDEMP-001")
                .billingPeriod("2026-05")
                .claimedAmount(new BigDecimal("30000.00"))
                .invoiceDate(LocalDate.of(2026, 5, 31))
                .build());

        ReconciliationRequest reconReq = ReconciliationRequest.builder()
                .settlementRunId(run.getId())
                .vendorInvoiceId(invoice.getId())
                .build();

        ReconciliationResponse first = reconciliationService.reconcile(reconReq, "ADMIN_1");
        long countAfterFirst = reconciliationRepository.count();

        ReconciliationResponse second = reconciliationService.reconcile(reconReq, "ADMIN_2");
        long countAfterSecond = reconciliationRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
        assertThat(second.getReconciliationId()).isEqualTo(first.getReconciliationId());
    }

    @Test
    @Order(7)
    @DisplayName("Reconciliation Scope Mismatch: Throws exception when billing period does not match")
    void testReconcileBillingPeriodMismatch() {
        SettlementRun run = createTestRun(new BigDecimal("10000.00"), "2026-06", vendor);

        VendorInvoiceResponse invoice = reconciliationService.submitInvoice(VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-MISMATCH-001")
                .billingPeriod("2026-07")
                .claimedAmount(new BigDecimal("10000.00"))
                .invoiceDate(LocalDate.of(2026, 7, 31))
                .build());

        ReconciliationRequest reconReq = ReconciliationRequest.builder()
                .settlementRunId(run.getId())
                .vendorInvoiceId(invoice.getId())
                .build();

        assertThatThrownBy(() -> reconciliationService.reconcile(reconReq, "ADMIN"))
                .isInstanceOf(SettlementDomainException.class)
                .hasMessageContaining("billing period mismatch");
    }

    @Test
    @Order(8)
    @DisplayName("Lists vendor invoices filtered by vendor ID and billing period")
    void testListInvoices() {
        List<VendorInvoiceResponse> filtered = reconciliationService.listInvoices(VENDOR_ID, "2026-01");
        assertThat(filtered).isNotEmpty();
        assertThat(filtered).allMatch(inv -> inv.getBillingPeriod().equals("2026-01"));
    }
}
