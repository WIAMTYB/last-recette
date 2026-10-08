package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SchedulerRunRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class SchedulerRunQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    private final SchedulerRunRepository schedulerRunRepository;
    private final PlanBatchRunner planBatchRunner;
    private final SchedulerProperties schedulerProperties;
    private final Clock clock;

    public SchedulerRunQueryService(SchedulerRunRepository schedulerRunRepository,
                                    PlanBatchRunner planBatchRunner,
                                    SchedulerProperties schedulerProperties,
                                    Clock clock) {
        this.schedulerRunRepository = schedulerRunRepository;
        this.planBatchRunner = planBatchRunner;
        this.schedulerProperties = schedulerProperties;
        this.clock = clock;
    }

    public Page<SchedulerRun> listRuns(String trigger, SchedulerRunOutcome outcome, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        boolean hasTrigger = trigger != null && !trigger.isBlank();
        if (hasTrigger && outcome != null) {
            return schedulerRunRepository.findByTriggerAndOutcome(trigger.trim(), outcome, pageable);
        }
        if (hasTrigger) {
            return schedulerRunRepository.findByTrigger(trigger.trim(), pageable);
        }
        if (outcome != null) {
            return schedulerRunRepository.findByOutcome(outcome, pageable);
        }
        return schedulerRunRepository.findAll(pageable);
    }

    public SchedulerRun getRun(long id) {
        return schedulerRunRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SCHEDULER_RUN_NOT_FOUND, "Scheduler run not found: " + id));
    }

    public Optional<SchedulerRun> currentRun() {
        return planBatchRunner.currentRun();
    }

    public Optional<SchedulerRun> lastFinishedRun() {
        return schedulerRunRepository.findFirstByOutcomeNotOrderByStartedAtDesc(SchedulerRunOutcome.RUNNING);
    }

    public ZonedDateTime nextScheduledAt() {
        ZoneId zone = ZoneId.of(schedulerProperties.getZone());
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        return CronExpression.parse(schedulerProperties.getCron()).next(now);
    }
}
