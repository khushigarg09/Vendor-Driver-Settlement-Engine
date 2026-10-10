package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.SettlementRunRequest;
import com.moveinsync.settlement.dto.SettlementRunResponse;
import com.moveinsync.settlement.repository.SettlementRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Concurrency Integration: SettlementRun Concurrent Execution & Race-Condition Safety")
class SettlementRunConcurrencyIntegrationTest {

    @Autowired
    private SettlementRunService settlementRunService;

    @Autowired
    private SettlementRunRepository settlementRunRepository;

    private static final UUID VENDOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BU_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BILLING_PERIOD = "2026-10";

    @Test
    @DisplayName("Concurrent threads executing the same settlement run scope produce exactly 1 database record")
    void testConcurrentSettlementRuns() throws InterruptedException, ExecutionException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        SettlementRunRequest request = SettlementRunRequest.builder()
                .vendorId(VENDOR_ID)
                .businessUnitId(BU_ID)
                .billingPeriod(BILLING_PERIOD)
                .build();

        List<Future<SettlementRunResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String threadUser = "CONCURRENT_WORKER_" + i;
            futures.add(executor.submit(() -> {
                startLatch.await(); // synchronous barrier release
                return settlementRunService.executeSettlementRun(request, threadUser);
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();

        int successfulResponses = 0;
        for (Future<SettlementRunResponse> future : futures) {
            try {
                SettlementRunResponse response = future.get();
                if (response != null && response.getId() != null) {
                    successfulResponses++;
                }
            } catch (ExecutionException e) {
                // If a concurrent thread encounters conflict or unique constraint, it is handled gracefully
            }
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verification: Exactly 1 settlement run must exist in the database for this vendor, BU, and period
        long runsInDb = settlementRunRepository
                .findAll()
                .stream()
                .filter(r -> r.getVendor().getId().equals(VENDOR_ID)
                        && r.getBusinessUnit().getId().equals(BU_ID)
                        && r.getBillingPeriod().equals(BILLING_PERIOD))
                .count();

        assertThat(runsInDb).isEqualTo(1);
    }
}
