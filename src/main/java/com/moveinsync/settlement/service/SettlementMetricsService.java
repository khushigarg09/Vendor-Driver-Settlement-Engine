package com.moveinsync.settlement.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordSettlementRun(String status, long durationMillis, double totalPayout) {
        Counter.builder("settlement.runs.total")
                .tag("status", status)
                .description("Total number of settlement runs executed")
                .register(meterRegistry)
                .increment();

        Timer.builder("settlement.runs.duration")
                .tag("status", status)
                .description("Execution time of settlement runs")
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);

        meterRegistry.summary("settlement.runs.payout.amount")
                .record(totalPayout);
    }

    public void recordReconciliation(String outcome, double varianceAmount) {
        Counter.builder("settlement.reconciliations.total")
                .tag("outcome", outcome)
                .description("Total number of invoice reconciliations evaluated")
                .register(meterRegistry)
                .increment();

        meterRegistry.summary("settlement.reconciliations.variance")
                .record(Math.abs(varianceAmount));
    }

    public void recordDispute(String action, String status, double deltaAmount) {
        Counter.builder("settlement.disputes.total")
                .tag("action", action)
                .tag("status", status)
                .description("Total number of dispute lifecycle actions")
                .register(meterRegistry)
                .increment();

        meterRegistry.summary("settlement.disputes.delta.amount")
                .record(Math.abs(deltaAmount));
    }
}
