package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "EPARGNE_PLAN_EXECUTION")
public class PlanExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Eager on purpose: the service runs as a GraalVM native image where Spring Boot pins
     * Hibernate's bytecode provider to {@code none}, so a lazy to-one would need a runtime
     * HibernateProxy and fail with "Generation of HibernateProxy instances at runtime is not
     * allowed" whenever an execution is loaded without its plan already in the session
     * (the executions listing). One plan row per listing makes eager loading cheap.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "PLAN_ID", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "EXECUTED_AT", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "CYCLE_DUE_DATE", nullable = false)
    private LocalDate cycleDueDate;

    @Column(name = "ATTEMPT_NUMBER", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "RESULT_STATUS", nullable = false, length = 30)
    private ExecutionResultStatus resultStatus;

    @Column(name = "EXECUTION_KEY", nullable = false, length = 120)
    private String executionKey;

    @Column(name = "EXTERNAL_ORDER_REFERENCE", length = 120)
    private String externalOrderReference;

    @Column(name = "MESSAGE", length = 1000)
    private String message;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public LocalDate getCycleDueDate() {
        return cycleDueDate;
    }

    public void setCycleDueDate(LocalDate cycleDueDate) {
        this.cycleDueDate = cycleDueDate;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public ExecutionResultStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(ExecutionResultStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getExecutionKey() {
        return executionKey;
    }

    public void setExecutionKey(String executionKey) {
        this.executionKey = executionKey;
    }

    public String getExternalOrderReference() {
        return externalOrderReference;
    }

    public void setExternalOrderReference(String externalOrderReference) {
        this.externalOrderReference = externalOrderReference;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
