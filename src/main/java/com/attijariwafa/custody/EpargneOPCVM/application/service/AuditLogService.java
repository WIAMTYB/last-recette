package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.domain.audit.AuditEvent;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.AuditEventRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditLogService {

    private final AuditEventRepository auditEventRepository;
    private final JsonMapper jsonMapper;

    public AuditLogService(AuditEventRepository auditEventRepository, JsonMapper jsonMapper) {
        this.auditEventRepository = auditEventRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void log(String eventType, String entityType, String entityReference, String status, Object payload, String errorMessage) {
        AuditEvent event = new AuditEvent();
        event.setEventTime(LocalDateTime.now());
        event.setEventType(eventType);
        event.setEntityType(entityType);
        event.setEntityReference(entityReference);
        event.setStatus(status);
        event.setActor(resolveActor());
        event.setCorrelationId(resolveCorrelationId());
        event.setPayload(toJson(payload));
        event.setErrorMessage(errorMessage);
        auditEventRepository.save(event);
    }

    private String resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "SYSTEM";
        }
        return authentication.getName();
    }

    private String resolveCorrelationId() {
        return CorrelationIds.currentOrCreate();
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            return "{\"serialization\":\"failed\"}";
        }
    }
}
