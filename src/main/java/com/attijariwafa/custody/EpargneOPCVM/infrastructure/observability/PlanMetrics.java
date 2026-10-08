package com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class PlanMetrics {

    private final MeterRegistry meterRegistry;

    public PlanMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordExternalCall(String service, String outcome, Duration duration) {
        meterRegistry.counter("epargne_external_service_calls_total", "service", service, "outcome", outcome)
                .increment();
        Timer.builder("epargne_external_service_latency")
                .tag("service", service)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    public void recordExternalRateLimited(String service) {
        meterRegistry.counter("epargne_external_service_rate_limited_total", "service", service)
                .increment();
    }

    public void recordSchedulerRun(String trigger, String outcome, Duration duration, int processedPlans) {
        meterRegistry.counter("epargne_scheduler_runs_total", "trigger", trigger, "outcome", outcome)
                .increment();
        Timer.builder("epargne_scheduler_run_duration")
                .tag("trigger", trigger)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
        if (processedPlans > 0) {
            meterRegistry.counter("epargne_scheduler_plans_processed_total", "trigger", trigger)
                    .increment(processedPlans);
        }
    }

    public void recordPlanExecution(ExecutionResultStatus resultStatus) {
        meterRegistry.counter("epargne_plan_executions_total", "result", resultStatus.name())
                .increment();
    }

    public void recordPlanSuspension(String reason) {
        meterRegistry.counter("epargne_plan_suspensions_total", "reason", reason)
                .increment();
    }

    public void recordPlanCycleSkipped(String reason) {
        meterRegistry.counter("epargne_plan_cycles_skipped_total", "reason", reason)
                .increment();
    }

    public void recordPlanRetryScheduled(String reason) {
        meterRegistry.counter("epargne_plan_retries_scheduled_total", "reason", reason)
                .increment();
    }
}