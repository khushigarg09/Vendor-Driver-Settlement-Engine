package com.moveinsync.settlement.service;

import com.moveinsync.settlement.dto.AuditLogResponse;

import java.util.List;
import java.util.Map;

public interface AuditService {

    void recordEvent(String action, String entityType, String entityId, String performedBy, String performedRole, Map<String, Object> details);

    List<AuditLogResponse> getAllAuditLogs();

    List<AuditLogResponse> getAuditLogsByEntity(String entityType, String entityId);
}
