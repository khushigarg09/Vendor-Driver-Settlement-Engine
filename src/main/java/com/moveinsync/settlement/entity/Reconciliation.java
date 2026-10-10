package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.entity.enums.ReconciliationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "reconciliations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_reconciliation_pair",
        columnNames = {"settlement_run_id", "vendor_invoice_id"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reconciliation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_run_id", nullable = false)
    private SettlementRun settlementRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_invoice_id", nullable = false)
    private VendorInvoice vendorInvoice;

    @Column(name = "system_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal systemAmount;

    @Column(name = "invoice_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "variance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal varianceAmount;

    @Column(name = "tolerance_applied", nullable = false, precision = 10, scale = 2)
    private BigDecimal toleranceApplied;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReconciliationStatus status;

    @Column(name = "explanation", length = 500)
    private String explanation;

    @Builder.Default
    @Column(name = "reconciled_at", nullable = false)
    private Instant reconciledAt = Instant.now();

    @Column(name = "reconciled_by", nullable = false, length = 100)
    private String reconciledBy;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (reconciledAt == null) {
            reconciledAt = Instant.now();
        }
    }
}
