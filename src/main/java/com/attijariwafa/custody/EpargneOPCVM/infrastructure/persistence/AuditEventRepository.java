package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;

import com.attijariwafa.custody.EpargneOPCVM.domain.audit.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByEntityTypeAndEntityReferenceAndEventTypeInOrderByEventTimeDesc(
            String entityType,
            String entityReference,
            Collection<String> eventTypes
    );



}















