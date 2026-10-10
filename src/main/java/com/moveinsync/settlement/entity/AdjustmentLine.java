package com.moveinsync.settlement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adjustment_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id", nullable = false)
    private Adjustment adjustment;

    @Column(name = "signed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal signedAmount;

    @Column(name = "description", nullable = false)
    private String description;

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
    }
}
