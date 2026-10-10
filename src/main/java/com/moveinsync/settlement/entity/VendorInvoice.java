package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "vendor_invoices",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_vendor_invoice_number",
        columnNames = {"vendor_id", "invoice_number"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorInvoice {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_unit_id", nullable = false)
    private BusinessUnit businessUnit;

    @Column(name = "invoice_number", nullable = false, length = 100)
    private String invoiceNumber;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Column(name = "claimed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "SUBMITTED";

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "SUBMITTED";
        }
    }
}
