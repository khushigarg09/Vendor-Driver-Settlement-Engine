package com.moveinsync.settlement.dto;

import com.moveinsync.settlement.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String action,
        String entityType,
        String entityId,
        String performedBy,
        String performedRole,
        String detailsJson,
        Instant createdAt
) {
    public static AuditLogResponse fromEntity(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getPerformedBy(),
                log.getPerformedRole(),
                log.getDetailsJson(),
                log.getCreatedAt()
        );
    }
}
