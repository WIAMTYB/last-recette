package com.attijariwafa.custody.EpargneOPCVM.domain.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "EPARGNE_AUDIT_EVENT")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EVENT_TIME", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "EVENT_TYPE", nullable = false, length = 80)
    private String eventType;

    @Column(name = "ENTITY_TYPE", nullable = false, length = 60)
    private String entityType;

    @Column(name = "ENTITY_REFERENCE", nullable = false, length = 120)
    private String entityReference;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "ACTOR", nullable = false, length = 120)
    private String actor;

    @Column(name = "CORRELATION_ID", nullable = false, length = 120)
    private String correlationId;

    @Lob
    @Column(name = "PAYLOAD")
    private String payload;

    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityReference() {
        return entityReference;
    }

    public void setEntityReference(String entityReference) {
        this.entityReference = entityReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
