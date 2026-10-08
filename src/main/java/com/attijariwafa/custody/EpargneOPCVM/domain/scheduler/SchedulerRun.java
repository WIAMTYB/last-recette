package com.attijariwafa.custody.EpargneOPCVM.domain.scheduler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * One execution batch of the recurring-plan scheduler, whether fired by the cron or
 * triggered manually. The row is inserted as RUNNING before the batch starts and
 * closed with the per-outcome counters once it ends.
 */
@Entity
@Table(name = "EPARGNE_SCHEDULER_RUN")
public class SchedulerRun {

    public static final String TRIGGER_SCHEDULED = "scheduled";
    public static final String TRIGGER_MANUAL = "manual";
    public static final int PROCESSED_REFERENCES_MAX_LENGTH = 4000;
    public static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TRIGGER_TYPE", nullable = false, length = 20)
    private String trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, length = 20)
    private SchedulerRunOutcome outcome;

    @Column(name = "STARTED_AT", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    @Column(name = "DURATION_MS")
    private Long durationMs;

    @Column(name = "BATCH_SIZE", nullable = false)
    private int batchSize;

    @Column(name = "PROCESSED_COUNT", nullable = false)
    private int processedCount;

    @Column(name = "SUCCESS_COUNT", nullable = false)
    private int successCount;

    @Column(name = "INSUFFICIENT_FUNDS_COUNT", nullable = false)
    private int insufficientFundsCount;

    @Column(name = "BELOW_THRESHOLD_COUNT", nullable = false)
    private int belowThresholdCount;

    @Column(name = "ORDER_REJECTED_COUNT", nullable = false)
    private int orderRejectedCount;

    @Column(name = "TECHNICAL_FAILURE_COUNT", nullable = false)
    private int technicalFailureCount;

    @Column(name = "RETRIES_SCHEDULED_COUNT", nullable = false)
    private int retriesScheduledCount;

    @Column(name = "CYCLES_SKIPPED_COUNT", nullable = false)
    private int cyclesSkippedCount;

    @Column(name = "NON_BUSINESS_DAY_DEFERRED_COUNT", nullable = false)
    private int nonBusinessDayDeferredCount;

    @Column(name = "PROCESSED_REFERENCES", length = PROCESSED_REFERENCES_MAX_LENGTH)
    private String processedReferences;

    @Column(name = "CORRELATION_ID", nullable = false, length = 120)
    private String correlationId;

    @Column(name = "TRIGGERED_BY", length = 120)
    private String triggeredBy;

    @Column(name = "ERROR_MESSAGE", length = ERROR_MESSAGE_MAX_LENGTH)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public SchedulerRunOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(SchedulerRunOutcome outcome) {
        this.outcome = outcome;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(int processedCount) {
        this.processedCount = processedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getInsufficientFundsCount() {
        return insufficientFundsCount;
    }

    public void setInsufficientFundsCount(int insufficientFundsCount) {
        this.insufficientFundsCount = insufficientFundsCount;
    }

    public int getBelowThresholdCount() {
        return belowThresholdCount;
    }

    public void setBelowThresholdCount(int belowThresholdCount) {
        this.belowThresholdCount = belowThresholdCount;
    }

    public int getOrderRejectedCount() {
        return orderRejectedCount;
    }

    public void setOrderRejectedCount(int orderRejectedCount) {
        this.orderRejectedCount = orderRejectedCount;
    }

    public int getTechnicalFailureCount() {
        return technicalFailureCount;
    }

    public void setTechnicalFailureCount(int technicalFailureCount) {
        this.technicalFailureCount = technicalFailureCount;
    }

    public int getRetriesScheduledCount() {
        return retriesScheduledCount;
    }

    public void setRetriesScheduledCount(int retriesScheduledCount) {
        this.retriesScheduledCount = retriesScheduledCount;
    }

    public int getCyclesSkippedCount() {
        return cyclesSkippedCount;
    }

    public void setCyclesSkippedCount(int cyclesSkippedCount) {
        this.cyclesSkippedCount = cyclesSkippedCount;
    }

    public int getNonBusinessDayDeferredCount() {
        return nonBusinessDayDeferredCount;
    }

    public void setNonBusinessDayDeferredCount(int nonBusinessDayDeferredCount) {
        this.nonBusinessDayDeferredCount = nonBusinessDayDeferredCount;
    }

    public String getProcessedReferences() {
        return processedReferences;
    }

    public void setProcessedReferences(String processedReferences) {
        this.processedReferences = processedReferences;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
