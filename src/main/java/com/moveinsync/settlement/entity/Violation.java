package com.moveinsync.settlement.entity;

import com.moveinsync.settlement.entity.enums.ViolationStatus;
import com.moveinsync.settlement.entity.enums.ViolationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "violations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {

    @Id
    private UUID id;

    @Column(name = "source_violation_id", nullable = false, unique = true, length = 100)
    private String sourceViolationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 50)
    private ViolationType violationType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "penalty_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal penaltyAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ViolationStatus status = ViolationStatus.PENDING;

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
            status = ViolationStatus.PENDING;
        }
    }
}
