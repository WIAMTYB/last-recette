package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SchedulerRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the scheduler run row in its own transactions so the RUNNING marker is visible
 * while the batch executes and the final counters survive whatever the batch did.
 */
@Service
public class SchedulerRunRecorder {

    private final SchedulerRunRepository schedulerRunRepository;
    private final Clock clock;

    public SchedulerRunRecorder(SchedulerRunRepository schedulerRunRepository, Clock clock) {
        this.schedulerRunRepository = schedulerRunRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulerRun start(String trigger, String triggeredBy, int batchSize, String correlationId) {
        SchedulerRun run = new SchedulerRun();
        run.setTrigger(trigger);
        run.setTriggeredBy(truncate(triggeredBy, 120));
        run.setOutcome(SchedulerRunOutcome.RUNNING);
        run.setStartedAt(LocalDateTime.now(clock));
        run.setBatchSize(batchSize);
        run.setCorrelationId(correlationId);
        return schedulerRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulerRun finish(Long runId, DuePlanBatchResult result, Duration elapsed) {
        SchedulerRun run = load(runId);
        apply(run, result);
        close(run, SchedulerRunOutcome.SUCCESS, elapsed);
        return schedulerRunRepository.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulerRun fail(Long runId, DuePlanBatchResult partialResult, Duration elapsed, String errorMessage) {
        SchedulerRun run = load(runId);
        if (partialResult != null) {
            apply(run, partialResult);
        }
        run.setErrorMessage(truncate(errorMessage, SchedulerRun.ERROR_MESSAGE_MAX_LENGTH));
        close(run, SchedulerRunOutcome.FAILURE, elapsed);
        return schedulerRunRepository.save(run);
    }

    private SchedulerRun load(Long runId) {
        return schedulerRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Scheduler run " + runId + " vanished before it finished"));
    }

    private void apply(SchedulerRun run, DuePlanBatchResult result) {
        run.setProcessedCount(result.processedCount());
        run.setSuccessCount(result.successCount());
        run.setInsufficientFundsCount(result.insufficientFundsCount());
        run.setBelowThresholdCount(result.belowThresholdCount());
        run.setOrderRejectedCount(result.orderRejectedCount());
        run.setTechnicalFailureCount(result.technicalFailureCount());
        run.setRetriesScheduledCount(result.retriesScheduledCount());
        run.setCyclesSkippedCount(result.cyclesSkippedCount());
        run.setNonBusinessDayDeferredCount(result.nonBusinessDayDeferredCount());
        run.setProcessedReferences(truncate(
                String.join(",", result.references()),
                SchedulerRun.PROCESSED_REFERENCES_MAX_LENGTH));
    }

    private void close(SchedulerRun run, SchedulerRunOutcome outcome, Duration elapsed) {
        run.setOutcome(outcome);
        run.setFinishedAt(LocalDateTime.now(clock));
        run.setDurationMs(elapsed.toMillis());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
