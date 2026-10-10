package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.entity.enums.SettlementLineType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_lines")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 50)
    private SettlementLineType lineType;

    @Column(name = "signed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal signedAmount;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "source_reference_id", length = 100)
    private String sourceReferenceId;

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
