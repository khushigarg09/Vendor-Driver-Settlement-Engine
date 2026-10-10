package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.entity.enums.SettlementRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "settlement_runs",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_settlement_run_scope",
        columnNames = {"vendor_id", "business_unit_id", "billing_period"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRun {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Builder.Default
    @Column(name = "run_number", nullable = false)
    private Integer runNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementRunStatus status;

    @Builder.Default
    @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_incentives", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIncentives = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_penalties", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPenalties = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_tds", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalTds = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_net_payout", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNetPayout = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @OneToMany(mappedBy = "settlementRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Settlement> settlements = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (totalGross == null) totalGross = BigDecimal.ZERO;
        if (totalIncentives == null) totalIncentives = BigDecimal.ZERO;
        if (totalPenalties == null) totalPenalties = BigDecimal.ZERO;
        if (totalTds == null) totalTds = BigDecimal.ZERO;
        if (totalNetPayout == null) totalNetPayout = BigDecimal.ZERO;
    }
}
