package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.entity.enums.SettlementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_settlement_driver_run",
        columnNames = {"settlement_run_id", "driver_id"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_run_id", nullable = false)
    private SettlementRun settlementRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Builder.Default
    @Column(name = "trips_count", nullable = false)
    private Integer tripsCount = 0;

    @Column(name = "gross_base_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossBasePay;

    @Column(name = "incentives_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal incentivesTotal;

    @Column(name = "penalties_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal penaltiesTotal;

    @Column(name = "tds_tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal tdsTaxTotal;

    @Column(name = "net_payout", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPayout;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementStatus status;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementLine> lines = new ArrayList<>();

    public void addLine(SettlementLine line) {
        lines.add(line);
        line.setSettlement(this);
    }

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
