package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SchedulerRunRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Runs one due-plan batch and records it as a {@link SchedulerRun}. Shared by the cron
 * scheduler and the manual trigger so both leave the same audit trail and metrics.
 *
 * <p>Duplicate-execution safety still comes from the per-plan row lock and the execution
 * key (see AGENTS.md): two overlapping runs on different pods cannot book the same plan
 * twice. The guards here only stop operators from stacking manual runs: an in-process
 * flag for this pod, and a best-effort check for a fresh RUNNING row written by any pod.
 * A RUNNING row older than {@code plans.scheduler.stale-run-after} is ignored, so a pod
 * that died mid-batch does not block future runs and needs no restart-time repair.
 */
@Service
public class PlanBatchRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanBatchRunner.class);

    public record RunReport(SchedulerRun run, DuePlanBatchResult result) {
    }

    private final PlanExecutionService planExecutionService;
    private final SchedulerRunRecorder recorder;
    private final SchedulerRunRepository schedulerRunRepository;
    private final SchedulerProperties schedulerProperties;
    private final PlanMetrics planMetrics;
    private final TaskExecutor executor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public PlanBatchRunner(PlanExecutionService planExecutionService,
                           SchedulerRunRecorder recorder,
                           SchedulerRunRepository schedulerRunRepository,
                           SchedulerProperties schedulerProperties,
                           PlanMetrics planMetrics,
                           @Qualifier("schedulerRunExecutor") TaskExecutor executor,
                           Clock clock) {
        this.planExecutionService = planExecutionService;
        this.recorder = recorder;
        this.schedulerRunRepository = schedulerRunRepository;
        this.schedulerProperties = schedulerProperties;
        this.planMetrics = planMetrics;
        this.executor = executor;
        this.clock = clock;
    }

    /** Executes the batch on the calling thread and returns the closed run with its result. */
    public RunReport run(String trigger, String triggeredBy) {
        SchedulerRun run = claim(trigger, triggeredBy);
        return execute(run);
    }

    /** Records the run as RUNNING, hands the batch to the executor and returns immediately. */
    public SchedulerRun submit(String trigger, String triggeredBy) {
        SchedulerRun run = claim(trigger, triggeredBy);
        String correlationId = run.getCorrelationId();
        try {
            executor.execute(() -> {
                CorrelationIds.establish(correlationId);
                try {
                    execute(run);
                } catch (RuntimeException ex) {
                    LOGGER.error("Manual recurring-plan run {} failed", run.getId(), ex);
                } finally {
                    MDC.remove(CorrelationIds.MDC_KEY);
                }
            });
        } catch (RuntimeException ex) {
            running.set(false);
            recorder.fail(run.getId(), null, Duration.ZERO, "Could not start the batch: " + ex.getMessage());
            throw ex;
        }
        return run;
    }

    public Optional<SchedulerRun> currentRun() {
        return schedulerRunRepository.findFirstByOutcomeAndStartedAtAfterOrderByStartedAtDesc(
                SchedulerRunOutcome.RUNNING,
                LocalDateTime.now(clock).minus(schedulerProperties.getStaleRunAfter()));
    }

    private SchedulerRun claim(String trigger, String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw new ApplicationException(ErrorCode.SCHEDULER_RUN_IN_PROGRESS,
                    "A scheduler run is already in progress on this instance");
        }
        try {
            Optional<SchedulerRun> inProgress = currentRun();
            if (inProgress.isPresent()) {
                throw new ApplicationException(ErrorCode.SCHEDULER_RUN_IN_PROGRESS,
                        "Scheduler run " + inProgress.get().getId() + " started at "
                                + inProgress.get().getStartedAt() + " is still in progress");
            }
            String correlationId = CorrelationIds.currentOrCreate();
            return recorder.start(trigger, triggeredBy, schedulerProperties.getBatchSize(), correlationId);
        } catch (RuntimeException ex) {
            running.set(false);
            throw ex;
        }
    }

    private RunReport execute(SchedulerRun run) {
        long startedNanos = System.nanoTime();
        try {
            DuePlanBatchResult result = planExecutionService.executeDuePlans();
            Duration elapsed = elapsedSince(startedNanos);
            SchedulerRun closed = recorder.finish(run.getId(), result, elapsed);
            planMetrics.recordSchedulerRun(run.getTrigger(), "success", elapsed, result.processedCount());
            LOGGER.info("Recurring-plan run {} ({}) processed {} plans in {} ms: {} succeeded, {} retries, {} skipped",
                    run.getId(), run.getTrigger(), result.processedCount(), elapsed.toMillis(),
                    result.successCount(), result.retriesScheduledCount(), result.cyclesSkippedCount());
            return new RunReport(closed, result);
        } catch (DuePlanBatchFailedException ex) {
            Duration elapsed = elapsedSince(startedNanos);
            recordFailure(run, ex.partialResult(), elapsed, ex.getCause());
            throw ex.getCause();
        } catch (RuntimeException ex) {
            Duration elapsed = elapsedSince(startedNanos);
            recordFailure(run, null, elapsed, ex);
            throw ex;
        } finally {
            running.set(false);
        }
    }

    private void recordFailure(SchedulerRun run, DuePlanBatchResult partialResult, Duration elapsed, RuntimeException cause) {
        int processed = partialResult == null ? 0 : partialResult.processedCount();
        planMetrics.recordSchedulerRun(run.getTrigger(), "failure", elapsed, processed);
        try {
            recorder.fail(run.getId(), partialResult, elapsed, cause.getMessage() == null ? cause.toString() : cause.getMessage());
        } catch (RuntimeException recordingFailure) {
            LOGGER.error("Could not record the failure of recurring-plan run {}", run.getId(), recordingFailure);
        }
    }

    private static Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
