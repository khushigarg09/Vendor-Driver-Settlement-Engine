package com.moveinsync.settlement.repository;

import com.moveinsync.settlement.entity.ContractRate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Database: Effective-Dated Contract Rate Resolution Integration Tests")
class ContractRateRepositoryIntegrationTest {

    @Autowired
    private ContractRateRepository contractRateRepository;

    private final UUID contractId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("Trip on Oct 10th (before mid-month change) must resolve to ₹250.00")
    void testRateResolutionBeforeChange() {
        // Oct 10th, 2026 at 09:15 AM IST (03:45 AM UTC)
        Instant tripTimestamp = Instant.parse("2026-10-10T03:45:00Z");

        Optional<ContractRate> rateOpt = contractRateRepository.findApplicableRate(contractId, "SEDAN", tripTimestamp);

        assertThat(rateOpt).isPresent();
        assertThat(rateOpt.get().getRateAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("Trip on Oct 16th (after mid-month change) must resolve to ₹280.00")
    void testRateResolutionAfterChange() {
        // Oct 16th, 2026 at 18:45 PM IST (13:15 PM UTC)
        Instant tripTimestamp = Instant.parse("2026-10-16T13:15:00Z");

        Optional<ContractRate> rateOpt = contractRateRepository.findApplicableRate(contractId, "SEDAN", tripTimestamp);

        assertThat(rateOpt).isPresent();
        assertThat(rateOpt.get().getRateAmount()).isEqualByComparingTo(new BigDecimal("280.00"));
    }

    @Test
    @DisplayName("Trip with non-existent vehicle category should return empty")
    void testRateResolutionUnknownCategory() {
        Instant tripTimestamp = Instant.parse("2026-10-10T03:45:00Z");

        Optional<ContractRate> rateOpt = contractRateRepository.findApplicableRate(contractId, "BUS", tripTimestamp);

        assertThat(rateOpt).isEmpty();
    }
}
