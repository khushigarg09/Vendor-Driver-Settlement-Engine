package com.moveinsync.settlement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.DisputeResolutionRequest;
import com.moveinsync.settlement.dto.ReconciliationRequest;
import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.VendorInvoiceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID DRIVER_1_ID = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID DRIVER_2_ID = UUID.fromString("44444444-4444-4444-4444-444444444442");

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/settlement-runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("ROLE_EMPLOYEE attempting to execute settlement run returns 403 Forbidden")
    void testEmployeeCannotExecuteSettlementRun() throws Exception {
        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .businessUnitId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .billingPeriod("2026-10")
                .build();

        mockMvc.perform(post("/api/v1/settlement-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("ROLE_EMPLOYEE attempting to approve settlement run returns 403 Forbidden")
    void testEmployeeCannotApproveSettlementRun() throws Exception {
        mockMvc.perform(post("/api/v1/settlement-runs/" + UUID.randomUUID() + "/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("ROLE_EMPLOYEE attempting to submit vendor invoice returns 403 Forbidden")
    void testEmployeeCannotSubmitVendorInvoice() throws Exception {
        VendorInvoiceRequest request = VendorInvoiceRequest.builder()
                .vendorId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .businessUnitId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .invoiceNumber("INV-UNAUTH-01")
                .billingPeriod("2026-10")
                .claimedAmount(new BigDecimal("1000.00"))
                .invoiceDate(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/v1/vendor-invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("ROLE_EMPLOYEE attempting to reconcile invoice returns 403 Forbidden")
    void testEmployeeCannotReconcile() throws Exception {
        ReconciliationRequest request = ReconciliationRequest.builder()
                .settlementRunId(UUID.randomUUID())
                .vendorInvoiceId(UUID.randomUUID())
                .tolerance(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(post("/api/v1/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("ROLE_EMPLOYEE attempting to approve dispute returns 403 Forbidden")
    void testEmployeeCannotApproveDispute() throws Exception {
        DisputeResolutionRequest request = DisputeResolutionRequest.builder()
                .approvedDelta(new BigDecimal("50.00"))
                .notes("Unauthorized approval attempt")
                .build();

        mockMvc.perform(post("/api/v1/disputes/" + UUID.randomUUID() + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("ROLE_EMPLOYEE attempting to view system audit logs returns 403 Forbidden")
    void testEmployeeCannotViewAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "approver", roles = {"APPROVER"})
    @DisplayName("ROLE_APPROVER attempting to view global audit logs returns 403 Forbidden (ADMIN only)")
    void testApproverCannotViewGlobalAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("ROLE_ADMIN can view global audit logs")
    void testAdminCanViewAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("Resource-level authorization: employee cannot view another driver's payslip (returns 403 Forbidden)")
    void testEmployeeCannotViewOtherDriverPayslip() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/" + DRIVER_2_ID + "/payslips/2026-10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "driver2", roles = {"EMPLOYEE"})
    @DisplayName("Resource-level authorization: driver2 cannot view driver 1's payslip (returns 403 Forbidden)")
    void testDriver2CannotViewDriver1Payslip() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/" + DRIVER_1_ID + "/payslips/2026-10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = {"EMPLOYEE"})
    @DisplayName("Resource-level authorization: employee viewing their own driver payslip is authorized (not 403)")
    void testEmployeeCanAccessOwnDriverPayslip() throws Exception {
        // Driver 1 payslip will return 404 (if not yet calculated for this period) or 200, but strictly NOT 403 Forbidden!
        mockMvc.perform(get("/api/v1/drivers/" + DRIVER_1_ID + "/payslips/2099-01"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status, "Expected access to be permitted, got 403");
                });
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Privileged user: ADMIN can access any driver's payslip (not 403)")
    void testAdminCanAccessAnyDriverPayslip() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/" + DRIVER_2_ID + "/payslips/2099-01"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, status, "Expected admin access to be permitted, got 403");
                });
    }
}
