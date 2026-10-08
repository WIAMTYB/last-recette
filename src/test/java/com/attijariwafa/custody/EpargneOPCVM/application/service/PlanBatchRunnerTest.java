package com.attijariwafa.custody.EpargneOPCVM.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.attijariwafa.custody.EpargneOPCVM.application.service.DuePlanBatchResult.Disposition;
import com.attijariwafa.custody.EpargneOPCVM.application.service.DuePlanBatchResult.PlanOutcome;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SchedulerRunRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanBatchRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-16T17:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PlanExecutionService planExecutionService;
    @Mock
    private SchedulerRunRepository schedulerRunRepository;

    private SimpleMeterRegistry meterRegistry;
    private SchedulerProperties properties;
    private final List<SchedulerRun> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new SchedulerProperties();
        properties.setBatchSize(25);
        when(schedulerRunRepository.save(any())).thenAnswer(invocation -> {
            SchedulerRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(42L);
            }
            saved.add(run);
            return run;
        });
        when(schedulerRunRepository.findById(42L)).thenAnswer(invocation ->
                saved.isEmpty() ? Optional.empty() : Optional.of(saved.getLast()));
        when(schedulerRunRepository.findFirstByOutcomeAndStartedAtAfterOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldRecordSuccessfulRunWithCounters() {
        DuePlanBatchResult result = new DuePlanBatchResult(25);
        result.record(new PlanOutcome("EP000001", Disposition.EXECUTED, ExecutionResultStatus.SUCCESS));
        result.record(new PlanOutcome("EP000002", Disposition.RETRY_SCHEDULED, ExecutionResultStatus.INSUFFICIENT_FUNDS));
        result.record(new PlanOutcome("EP000003", Disposition.CYCLE_SKIPPED, ExecutionResultStatus.ORDER_REJECTED));
        result.record(new PlanOutcome("EP000004", Disposition.NON_BUSINESS_DAY_DEFERRED, null));
        when(planExecutionService.executeDuePlans()).thenReturn(result);

        PlanBatchRunner.RunReport report = newRunner(new SyncTaskExecutor()).run("manual", "CARGO");

        SchedulerRun run = report.run();
        assertThat(run.getId()).isEqualTo(42L);
        assertThat(run.getTrigger()).isEqualTo("manual");
        assertThat(run.getTriggeredBy()).isEqualTo("CARGO");
        assertThat(run.getOutcome()).isEqualTo(SchedulerRunOutcome.SUCCESS);
        assertThat(run.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 9, 16, 17, 0));
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getDurationMs()).isNotNull();
        assertThat(run.getBatchSize()).isEqualTo(25);
        assertThat(run.getProcessedCount()).isEqualTo(4);
        assertThat(run.getSuccessCount()).isEqualTo(1);
        assertThat(run.getInsufficientFundsCount()).isEqualTo(1);
        assertThat(run.getOrderRejectedCount()).isEqualTo(1);
        assertThat(run.getRetriesScheduledCount()).isEqualTo(1);
        assertThat(run.getCyclesSkippedCount()).isEqualTo(1);
        assertThat(run.getNonBusinessDayDeferredCount()).isEqualTo(1);
        assertThat(run.getProcessedReferences()).isEqualTo("EP000001,EP000002,EP000003,EP000004");
        assertThat(run.getCorrelationId()).isNotBlank();
        assertThat(run.getErrorMessage()).isNull();
        assertThat(report.result()).isSameAs(result);
        // The RUNNING row is written before the batch and closed after it.
        assertThat(saved).hasSize(2);
        assertThat(meterRegistry.counter("epargne_scheduler_runs_total", "trigger", "manual", "outcome", "success").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("epargne_scheduler_plans_processed_total", "trigger", "manual").count())
                .isEqualTo(4);
    }

    @Test
    void shouldRecordFailureWithPartialCountersAndRethrowCause() {
        DuePlanBatchResult partial = new DuePlanBatchResult(25);
        partial.record(new PlanOutcome("EP000001", Disposition.EXECUTED, ExecutionResultStatus.SUCCESS));
        IllegalStateException cause = new IllegalStateException("connection lost");
        when(planExecutionService.executeDuePlans()).thenThrow(new DuePlanBatchFailedException(partial, cause));
        PlanBatchRunner runner = newRunner(new SyncTaskExecutor());

        assertThatThrownBy(() -> runner.run("scheduled", "SYSTEM")).isSameAs(cause);

        SchedulerRun run = saved.getLast();
        assertThat(run.getOutcome()).isEqualTo(SchedulerRunOutcome.FAILURE);
        assertThat(run.getProcessedCount()).isEqualTo(1);
        assertThat(run.getSuccessCount()).isEqualTo(1);
        assertThat(run.getErrorMessage()).isEqualTo("connection lost");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(meterRegistry.counter("epargne_scheduler_runs_total", "trigger", "scheduled", "outcome", "failure").count())
                .isEqualTo(1);
        // The guard is released so the next run can start.
        doReturn(new DuePlanBatchResult(25)).when(planExecutionService).executeDuePlans();
        assertThat(runner.run("scheduled", "SYSTEM").run().getOutcome()).isEqualTo(SchedulerRunOutcome.SUCCESS);
    }

    @Test
    void shouldRefuseWhenARunIsInProgressOnThisInstance() {
        AtomicReference<PlanBatchRunner> holder = new AtomicReference<>();
        when(planExecutionService.executeDuePlans()).thenAnswer(invocation -> {
            assertThatThrownBy(() -> holder.get().run("manual", "CARGO"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(ex -> ((ApplicationException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SCHEDULER_RUN_IN_PROGRESS);
            return new DuePlanBatchResult(25);
        });
        holder.set(newRunner(new SyncTaskExecutor()));

        holder.get().run("scheduled", "SYSTEM");

        verify(planExecutionService).executeDuePlans();
        assertThat(saved).hasSize(2);
    }

    @Test
    void shouldRefuseWhenAFreshRunningRowExists() {
        SchedulerRun other = new SchedulerRun();
        other.setId(7L);
        other.setOutcome(SchedulerRunOutcome.RUNNING);
        other.setStartedAt(LocalDateTime.of(2026, 9, 16, 16, 50));
        when(schedulerRunRepository.findFirstByOutcomeAndStartedAtAfterOrderByStartedAtDesc(
                eq(SchedulerRunOutcome.RUNNING), eq(LocalDateTime.of(2026, 9, 16, 16, 0))))
                .thenReturn(Optional.of(other));
        PlanBatchRunner runner = newRunner(new SyncTaskExecutor());

        assertThatThrownBy(() -> runner.run("manual", "CARGO"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("7");

        verify(planExecutionService, never()).executeDuePlans();
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldSubmitAndReturnRunningRowBeforeBatchExecutes() {
        List<Runnable> queued = new ArrayList<>();
        TaskExecutor deferred = queued::add;
        when(planExecutionService.executeDuePlans()).thenReturn(new DuePlanBatchResult(25));
        PlanBatchRunner runner = newRunner(deferred);

        SchedulerRun running = runner.submit("manual", "CARGO");

        assertThat(running.getOutcome()).isEqualTo(SchedulerRunOutcome.RUNNING);
        assertThat(running.getFinishedAt()).isNull();
        verify(planExecutionService, never()).executeDuePlans();
        assertThat(queued).hasSize(1);

        queued.getFirst().run();

        verify(planExecutionService).executeDuePlans();
        assertThat(saved.getLast().getOutcome()).isEqualTo(SchedulerRunOutcome.SUCCESS);
    }

    @Test
    void shouldCloseRunWhenExecutorRejectsTheBatch() {
        TaskExecutor rejecting = task -> {
            throw new IllegalStateException("executor saturated");
        };
        PlanBatchRunner runner = newRunner(rejecting);

        assertThatThrownBy(() -> runner.submit("manual", "CARGO")).hasMessage("executor saturated");

        assertThat(saved.getLast().getOutcome()).isEqualTo(SchedulerRunOutcome.FAILURE);
        assertThat(saved.getLast().getErrorMessage()).contains("executor saturated");
        when(planExecutionService.executeDuePlans()).thenReturn(new DuePlanBatchResult(25));
        assertThat(runner.run("manual", "CARGO").run().getOutcome()).isEqualTo(SchedulerRunOutcome.SUCCESS);
    }

    private PlanBatchRunner newRunner(TaskExecutor executor) {
        return new PlanBatchRunner(
                planExecutionService,
                new SchedulerRunRecorder(schedulerRunRepository, CLOCK),
                schedulerRunRepository,
                properties,
                new PlanMetrics(meterRegistry),
                executor,
                CLOCK);
    }
}
