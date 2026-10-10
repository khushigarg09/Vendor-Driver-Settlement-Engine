package com.moveinsync.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.DisputeRequest;
import com.moveinsync.settlement.dto.DisputeResolutionRequest;
import com.moveinsync.settlement.entity.*;
import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import com.moveinsync.settlement.entity.enums.SettlementStatus;
import com.moveinsync.settlement.repository.*;
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
@DisplayName("Controller Integration: DisputeController & AdjustmentController RBAC & Endpoints")
class DisputeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private BusinessUnitRepository businessUnitRepository;

    @Autowired
    private DriverRepository driverRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRIVER_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID TRIP_1_ID = UUID.fromString("66666666-6666-6666-6666-666666666661");
    private static final UUID VIOLATION_ID = UUID.fromString("77777777-7777-7777-7777-777777777771");

    private static UUID testSettlementId;
    private static UUID createdDispute1Id;
    private static UUID createdDispute2Id;

    private void ensureSettlementExists() {
        if (testSettlementId == null) {
            Vendor vendor = vendorRepository.findById(VENDOR_ID).orElseThrow();
            BusinessUnit bu = businessUnitRepository.findById(BU_ID).orElseThrow();
            Driver driver = driverRepository.findById(DRIVER_ID).orElseThrow();

            SettlementRun run = settlementRunRepository
                    .findByVendorIdAndBusinessUnitIdAndBillingPeriod(VENDOR_ID, BU_ID, "2026-06")
                    .orElseGet(() -> settlementRunRepository.save(SettlementRun.builder()
                            .id(UUID.randomUUID())
                            .vendor(vendor)
                            .businessUnit(bu)
                            .billingPeriod("2026-06")
                            .status(SettlementRunStatus.FINALIZED)
                            .totalGross(new BigDecimal("3000.00"))
                            .totalIncentives(BigDecimal.ZERO)
                            .totalPenalties(BigDecimal.ZERO)
                            .totalTds(BigDecimal.ZERO)
                            .totalNetPayout(new BigDecimal("3000.00"))
                            .createdBy("TEST_DISPUTE_CTRL")
                            .createdAt(Instant.now())
                            .build()));

            Settlement settlement = settlementRepository.save(Settlement.builder()
                    .id(UUID.randomUUID())
                    .settlementRun(run)
                    .driver(driver)
                    .billingPeriod("2026-06")
                    .tripsCount(2)
                    .grossBasePay(new BigDecimal("3000.00"))
                    .incentivesTotal(BigDecimal.ZERO)
                    .penaltiesTotal(BigDecimal.ZERO)
                    .tdsTaxTotal(BigDecimal.ZERO)
                    .netPayout(new BigDecimal("3000.00"))
                    .status(SettlementStatus.APPROVED)
                    .createdAt(Instant.now())
                    .build());

            testSettlementId = settlement.getId();
        }
    }

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/disputes with EMPLOYEE role files dispute (201)")
    void testFileDisputeEmployeeAllowed() throws Exception {
        ensureSettlementExists();

        DisputeRequest request = DisputeRequest.builder()
                .settlementId(testSettlementId)
                .disputedTripId(TRIP_1_ID)
                .reason("Rate revision not reflected.")
                .claimedDelta(new BigDecimal("30.00"))
                .build();

        String responseJson = mockMvc.perform(post("/api/v1/disputes")
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.claimedDelta", is(30.0)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        createdDispute1Id = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/disputes/{id}/approve with EMPLOYEE role is FORBIDDEN (403)")
    void testApproveDisputeEmployeeForbidden() throws Exception {
        DisputeResolutionRequest resolveReq = DisputeResolutionRequest.builder()
                .approvedDelta(new BigDecimal("30.00"))
                .notes("Employee trying to self-approve.")
                .build();

        mockMvc.perform(post("/api/v1/disputes/" + createdDispute1Id + "/approve")
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/v1/disputes/{id}/approve with APPROVER role approves dispute & returns adjustment (200)")
    void testApproveDisputeApproverAllowed() throws Exception {
        DisputeResolutionRequest resolveReq = DisputeResolutionRequest.builder()
                .approvedDelta(new BigDecimal("30.00"))
                .notes("Verified and approved.")
                .build();

        mockMvc.perform(post("/api/v1/disputes/" + createdDispute1Id + "/approve")
                        .with(httpBasic("approver", "approver123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeId", is(createdDispute1Id.toString())))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.adjustment", notNullValue()))
                .andExpect(jsonPath("$.adjustment.deltaAmount", is(30.0)))
                .andExpect(jsonPath("$.adjustment.status", is("APPROVED")));
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/v1/disputes with EMPLOYEE files second dispute for violation (201)")
    void testFileSecondDisputeForRejection() throws Exception {
        ensureSettlementExists();

        DisputeRequest request = DisputeRequest.builder()
                .settlementId(testSettlementId)
                .disputedViolationId(VIOLATION_ID)
                .reason("Speed sensor malfunction.")
                .claimedDelta(new BigDecimal("500.00"))
                .build();

        String responseJson = mockMvc.perform(post("/api/v1/disputes")
                        .with(httpBasic("employee", "employee123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        createdDispute2Id = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/v1/disputes/{id}/reject with APPROVER role rejects dispute (200)")
    void testRejectDisputeApproverAllowed() throws Exception {
        DisputeResolutionRequest rejectReq = DisputeResolutionRequest.builder()
                .notes("GPS sensor verified accurate; penalty confirmed.")
                .build();

        mockMvc.perform(post("/api/v1/disputes/" + createdDispute2Id + "/reject")
                        .with(httpBasic("approver", "approver123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdDispute2Id.toString())))
                .andExpect(jsonPath("$.status", is("REJECTED")));
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/v1/disputes/{id} with EMPLOYEE returns dispute details (200)")
    void testGetDisputeById() throws Exception {
        mockMvc.perform(get("/api/v1/disputes/" + createdDispute1Id)
                        .with(httpBasic("employee", "employee123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdDispute1Id.toString())))
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/v1/adjustments with APPROVER lists adjustments (200)")
    void testListAdjustments() throws Exception {
        mockMvc.perform(get("/api/v1/adjustments")
                        .with(httpBasic("approver", "approver123"))
                        .param("settlementId", testSettlementId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));
    }
}
