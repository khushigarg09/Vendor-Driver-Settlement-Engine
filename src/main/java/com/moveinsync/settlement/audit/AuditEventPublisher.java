package com.moveinsync.settlement.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class AuditEventPublisher {

    public void publishEvent(String action, String entityType, String entityId, String performedBy, Map<String, Object> details) {
        log.info("AUDIT_EVENT [action={}, entityType={}, entityId={}, user={}, timestamp={}, details={}]",
                action, entityType, entityId, performedBy, Instant.now(), details);
    }
}
