package com.moveinsync.settlement.config;

import com.moveinsync.settlement.domain.calculation.SettlementCalculationEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SettlementEngineConfig {

    @Bean
    public SettlementCalculationEngine settlementCalculationEngine() {
        return SettlementCalculationEngine.createDefault();
    }
}
