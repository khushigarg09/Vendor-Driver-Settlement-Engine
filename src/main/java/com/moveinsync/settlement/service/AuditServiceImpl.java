package com.moveinsync.settlement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.settlement.dto.AuditLogResponse;
import com.moveinsync.settlement.entity.AuditLog;
import com.moveinsync.settlement.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(String action, String entityType, String entityId, String performedBy, String performedRole, Map<String, Object> details) {
        String detailsJson = null;
        if (details != null && !details.isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit log details for action {}: {}", action, e.getMessage());
                detailsJson = details.toString();
            }
        }

        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(performedBy != null ? performedBy : "SYSTEM")
                .performedRole(performedRole)
                .detailsJson(detailsJson)
                .createdAt(Instant.now())
                .build();

        auditLogRepository.save(auditLog);

        log.info("PERSISTED_AUDIT_LOG: action={}, entityType={}, entityId={}, user={}, role={}",
                action, entityType, entityId, performedBy, performedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAllAuditLogs(int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 100);
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(boundedPage, boundedSize))
                .map(AuditLogResponse::fromEntity)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByEntity(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId).stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByEntity(String entityType, String entityId, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 100);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, PageRequest.of(boundedPage, boundedSize))
                .map(AuditLogResponse::fromEntity)
                .getContent();
    }
}
