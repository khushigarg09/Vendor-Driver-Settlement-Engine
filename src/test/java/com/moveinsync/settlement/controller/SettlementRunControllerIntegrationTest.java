package com.moveinsync.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.entity.SettlementRun;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Controller Integration: SettlementRunController & DriverPayslipController Endpoints")
class SettlementRunControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRIVER_1_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final String BILLING_PERIOD = "2026-10";

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/settlement-runs with ADMIN role returns 200 or 201 with summary")
    void testPostSettlementRunAdmin() throws Exception {
        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod(BILLING_PERIOD)
                .build();

        mockMvc.perform(post("/api/v1/settlement-runs")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.billingPeriod", is(BILLING_PERIOD)));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/settlement-runs with EMPLOYEE role returns 403 Forbidden")
    void testPostSettlementRunForbiddenForEmployee() throws Exception {
        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod(BILLING_PERIOD)
                .build();

        mockMvc.perform(post("/api/v1/settlement-runs")
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/settlement-runs returns list of runs")
    void testListSettlementRuns() throws Exception {
        mockMvc.perform(get("/api/v1/settlement-runs")
                        .with(httpBasic("approver", "approver123"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/drivers/{driverId}/payslips/{billingPeriod} returns payslip with signed lines")
    void testGetDriverPayslip() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/{driverId}/payslips/{billingPeriod}", DRIVER_1_ID, BILLING_PERIOD)
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId", is(DRIVER_1_ID.toString())))
                .andExpect(jsonPath("$.driverCode", is("DRV-10042")))
                .andExpect(jsonPath("$.verification.isReconciled", is(true)))
                .andExpect(jsonPath("$.lineItems", notNullValue()));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/settlement-runs/{id}/approve approves run")
    void testApproveSettlementRun() throws Exception {
        SettlementRun run = settlementRunRepository
                .findByVendorIdAndBusinessUnitIdAndBillingPeriod(VENDOR_ID, BU_ID, BILLING_PERIOD)
                .orElseThrow();

        mockMvc.perform(post("/api/v1/settlement-runs/{id}/approve", run.getId())
                        .with(httpBasic("approver", "approver123"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FINALIZED")));
    }
}
