package com.moveinsync.settlement.observability;

import com.moveinsync.settlement.config.CorrelationIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CorrelationIdIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Automatically generates X-Correlation-ID header when not provided in request")
    void testGeneratesCorrelationIdWhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/settlement-runs"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .andExpect(header().string(
                        CorrelationIdFilter.CORRELATION_ID_HEADER,
                        matchesPattern("^[0-9a-fA-F-]{36}$")
                ));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Propagates client-provided X-Correlation-ID in response header")
    void testPropagatesClientCorrelationId() throws Exception {
        String clientTraceId = "client-trace-12345-abcdef";

        mockMvc.perform(get("/api/v1/settlement-runs")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, clientTraceId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, clientTraceId));
    }
}
