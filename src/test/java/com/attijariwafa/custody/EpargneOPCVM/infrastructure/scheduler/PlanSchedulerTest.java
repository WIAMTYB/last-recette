package com.attijariwafa.custody.EpargneOPCVM.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanBatchRunner;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

class PlanSchedulerTest {

    @Test
    void shouldRunScheduledBatchThroughRunnerAndClearCorrelation() {
        PlanBatchRunner runner = Mockito.mock(PlanBatchRunner.class);

        new PlanScheduler(runner).runScheduledExecution();

        verify(runner).run("scheduled", "SYSTEM");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void shouldSkipWhenAnotherRunIsInProgress() {
        PlanBatchRunner runner = Mockito.mock(PlanBatchRunner.class);
        when(runner.run("scheduled", "SYSTEM"))
                .thenThrow(new ApplicationException(ErrorCode.SCHEDULER_RUN_IN_PROGRESS, "busy"));

        new PlanScheduler(runner).runScheduledExecution();

        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void shouldPropagateOtherFailures() {
        PlanBatchRunner runner = Mockito.mock(PlanBatchRunner.class);
        when(runner.run("scheduled", "SYSTEM")).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> new PlanScheduler(runner).runScheduledExecution())
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }
}
