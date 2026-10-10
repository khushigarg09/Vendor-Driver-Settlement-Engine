package com.moveinsync.settlement.audit;

import com.moveinsync.settlement.dto.DisputeRequest;
import com.moveinsync.settlement.dto.DisputeResolutionRequest;
import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import com.moveinsync.settlement.entity.AuditLog;
import com.moveinsync.settlement.entity.Settlement;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Trip;
import com.moveinsync.settlement.entity.VendorInvoice;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.entity.enums.TripSettlementStatus;
import com.moveinsync.settlement.repository.*;
import com.moveinsync.settlement.service.DisputeAdjustmentService;
import com.moveinsync.settlement.service.ReconciliationService;
import com.moveinsync.settlement.service.SettlementRunService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuditTrailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private DriverRepository driverRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRIVER_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");

    @Test
    @DisplayName("Audit events are reliably persisted during settlement lifecycle actions")
    void testAuditEventsPersistedAcrossLifecycle() {
        String billingPeriod = "2027-01";

        // Seed 1 eligible trip in 2027-01 for the settlement run
        Trip testTrip = Trip.builder()
                .id(UUID.randomUUID())
                .sourceTripId("TRIP-AUDIT-2027-01")
                .driver(driverRepository.findById(DRIVER_ID).orElseThrow())
                .vendor(vendorRepository.findById(VENDOR_ID).orElseThrow())
                .businessUnit(businessUnitRepository.findById(BU_ID).orElseThrow())
                .vehicleCategory("SEDAN")
                .startedAt(Instant.parse("2027-01-10T08:00:00Z"))
                .completedAt(Instant.parse("2027-01-10T09:00:00Z"))
                .status(com.moveinsync.settlement.entity.enums.TripStatus.COMPLETED)
                .settlementStatus(com.moveinsync.settlement.entity.enums.TripSettlementStatus.PENDING)
                .delayMinutes(0)
                .createdAt(Instant.now())
                .build();
        tripRepository.save(testTrip);

        // 1. Initiate settlement run
        SettlementRunRequest runRequest = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod(billingPeriod)
                .build();

        var runResponse = settlementRunService.executeSettlementRun(runRequest, "AUDIT_ADMIN_USER");
        assertNotNull(runResponse);
        UUID runId = runResponse.getId();

        // Verify SETTLEMENT_RUN_INITIATED audit log
        List<AuditLog> initLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("SETTLEMENT_RUN", runId.toString());
        assertFalse(initLogs.isEmpty(), "Expected SETTLEMENT_RUN audit logs to be created");
        assertTrue(initLogs.stream().anyMatch(l -> l.getAction().equals("SETTLEMENT_RUN_INITIATED")));

        // 2. Approve settlement run
        settlementRunService.approveSettlementRun(runId, "AUDIT_APPROVER_USER");

        // Verify SETTLEMENT_RUN_APPROVED audit log
        List<AuditLog> approveLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("SETTLEMENT_RUN", runId.toString());
        assertTrue(approveLogs.stream().anyMatch(l -> l.getAction().equals("SETTLEMENT_RUN_APPROVED")));

        // 3. Submit Vendor Invoice
        VendorInvoiceRequest invRequest = VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-AUDIT-2027-01")
                .billingPeriod(billingPeriod)
                .claimedAmount(new BigDecimal("500.00"))
                .invoiceDate(LocalDate.now())
                .build();

        var invResponse = reconciliationService.submitInvoice(invRequest);
        assertNotNull(invResponse);
        UUID invoiceId = invResponse.getId();

        // Verify VENDOR_INVOICE_SUBMITTED audit log
        List<AuditLog> invLogs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("VENDOR_INVOICE", invoiceId.toString());
        assertFalse(invLogs.isEmpty(), "Expected VENDOR_INVOICE audit log");
        assertTrue(invLogs.stream().anyMatch(l -> l.getAction().equals("VENDOR_INVOICE_SUBMITTED")));

        // 4. Reconcile
        ReconciliationRequest reconRequest = ReconciliationRequest.builder()
                .settlementRunId(runId)
                .vendorInvoiceId(invoiceId)
                .tolerance(new BigDecimal("100.00"))
                .build();

        var reconResponse = reconciliationService.reconcile(reconRequest, "AUDIT_RECONCILER");
        assertNotNull(reconResponse);

        // Verify RECONCILIATION_PERFORMED audit log
        List<AuditLog> reconLogs = auditLogRepository.findByActionOrderByCreatedAtDesc("RECONCILIATION_PERFORMED");
        assertFalse(reconLogs.isEmpty(), "Expected RECONCILIATION_PERFORMED audit log");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can retrieve full audit log list via REST endpoint")
    void testAdminCanRetrieveAuditLogsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(username = "approver", roles = {"APPROVER"})
    @DisplayName("Approver can retrieve entity-specific audit history via REST endpoint")
    void testApproverCanRetrieveEntityAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/entity/SETTLEMENT_RUN/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
