package com.moveinsync.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import com.moveinsync.settlement.entity.BusinessUnit;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.entity.Vendor;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.repository.BusinessUnitRepository;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import com.moveinsync.settlement.repository.VendorInvoiceRepository;
import com.moveinsync.settlement.repository.VendorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Controller Integration: VendorInvoiceController & ReconciliationController Endpoints")
class VendorInvoiceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BILLING_PERIOD = "2026-08";

    private static UUID createdInvoiceId;
    private static UUID createdRunId;
    private static UUID createdReconciliationId;

    private void ensureSettlementRunCreated() {
        if (createdRunId == null) {
            Vendor vendor = vendorRepository.findById(VENDOR_ID).orElseThrow();
            BusinessUnit bu = businessUnitRepository.findById(BU_ID).orElseThrow();

            SettlementRun run = settlementRunRepository
                    .findByVendorIdAndBusinessUnitIdAndBillingPeriod(VENDOR_ID, BU_ID, BILLING_PERIOD)
                    .orElseGet(() -> {
                        SettlementRun newRun = SettlementRun.builder()
                                .id(UUID.randomUUID())
                                .vendor(vendor)
                                .businessUnit(bu)
                                .billingPeriod(BILLING_PERIOD)
                                .status(SettlementRunStatus.CALCULATED)
                                .totalGross(new BigDecimal("10000.00"))
                                .totalIncentives(BigDecimal.ZERO)
                                .totalPenalties(BigDecimal.ZERO)
                                .totalTds(BigDecimal.ZERO)
                                .totalNetPayout(new BigDecimal("10000.00"))
                                .createdBy("TEST_INIT")
                                .createdAt(Instant.now())
                                .build();
                        return settlementRunRepository.save(newRun);
                    });
            createdRunId = run.getId();
        }
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/vendor-invoices with ADMIN role creates invoice (201)")
    void testSubmitInvoiceAdmin() throws Exception {
        VendorInvoiceRequest request = VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-API-001")
                .billingPeriod(BILLING_PERIOD)
                .claimedAmount(new BigDecimal("10000.00"))
                .invoiceDate(LocalDate.of(2026, 8, 31))
                .build();

        String responseJson = mockMvc.perform(post("/api/v1/vendor-invoices")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.invoiceNumber", is("INV-API-001")))
                .andExpect(jsonPath("$.status", is("SUBMITTED")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        createdInvoiceId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/vendor-invoices duplicate invoice returns 409 Conflict")
    void testSubmitDuplicateInvoiceConflict() throws Exception {
        VendorInvoiceRequest request = VendorInvoiceRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .invoiceNumber("INV-API-001")
                .billingPeriod(BILLING_PERIOD)
                .claimedAmount(new BigDecimal("10000.00"))
                .invoiceDate(LocalDate.of(2026, 8, 31))
                .build();

        mockMvc.perform(post("/api/v1/vendor-invoices")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict Occurred")))
                .andExpect(jsonPath("$.detail", notNullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/vendor-invoices with APPROVER role lists invoices (200)")
    void testListInvoicesApprover() throws Exception {
        mockMvc.perform(get("/api/v1/vendor-invoices")
                        .with(httpBasic("approver", "approver123"))
                        .param("vendorId", VENDOR_ID.toString())
                        .param("billingPeriod", BILLING_PERIOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/vendor-invoices/{id} returns invoice details (200)")
    void testGetInvoiceById() throws Exception {
        mockMvc.perform(get("/api/v1/vendor-invoices/" + createdInvoiceId)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdInvoiceId.toString())))
                .andExpect(jsonPath("$.invoiceNumber", is("INV-API-001")));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/reconciliations with ADMIN role performs reconciliation (200)")
    void testReconcileAdmin() throws Exception {
        ensureSettlementRunCreated();

        ReconciliationRequest request = ReconciliationRequest.builder()
                .settlementRunId(createdRunId)
                .vendorInvoiceId(createdInvoiceId)
                .tolerance(new BigDecimal("50.00"))
                .build();

        String responseJson = mockMvc.perform(post("/api/v1/reconciliations")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationId", notNullValue()))
                .andExpect(jsonPath("$.status", is("EXACT_MATCH")))
                .andExpect(jsonPath("$.varianceAmount", is(0.0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        createdReconciliationId = UUID.fromString(objectMapper.readTree(responseJson).get("reconciliationId").asText());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/v1/reconciliations/{id} retrieves reconciliation record (200)")
    void testGetReconciliationById() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliations/" + createdReconciliationId)
                        .with(httpBasic("approver", "approver123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciliationId", is(createdReconciliationId.toString())))
                .andExpect(jsonPath("$.status", is("EXACT_MATCH")));
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/v1/reconciliations with EMPLOYEE role returns 403 Forbidden")
    void testReconciliationRbacEmployeeForbidden() throws Exception {
        ensureSettlementRunCreated();

        ReconciliationRequest request = ReconciliationRequest.builder()
                .settlementRunId(createdRunId)
                .vendorInvoiceId(createdInvoiceId)
                .build();

        mockMvc.perform(post("/api/v1/reconciliations")
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
