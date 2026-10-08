package com.attijariwafa.custody.EpargneOPCVM.infrastructure.scheduler;

import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanBatchRunner;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlanScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanScheduler.class);
    private static final String TRIGGERED_BY = "SYSTEM";

    private final PlanBatchRunner planBatchRunner;

    public PlanScheduler(PlanBatchRunner planBatchRunner) {
        this.planBatchRunner = planBatchRunner;
    }

    @Scheduled(cron = "${plans.scheduler.cron:0 0 18 * * *}", zone = "${plans.scheduler.zone:Africa/Casablanca}")
    public void runScheduledExecution() {
        CorrelationIds.currentOrCreate();
        try {
            planBatchRunner.run(SchedulerRun.TRIGGER_SCHEDULED, TRIGGERED_BY);
        } catch (ApplicationException ex) {
            if (ex.getErrorCode() != ErrorCode.SCHEDULER_RUN_IN_PROGRESS) {
                throw ex;
            }
            // A manual run overlapping the cron is not an error; the plans it leaves behind
            // are picked up by the next scheduled run.
            LOGGER.warn("Skipping scheduled recurring-plan execution: {}", ex.getMessage());
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
