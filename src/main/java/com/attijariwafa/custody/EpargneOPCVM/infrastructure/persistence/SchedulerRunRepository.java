package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;

import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerRunRepository extends JpaRepository<SchedulerRun, Long> {

    Page<SchedulerRun> findByTriggerAndOutcome(String trigger, SchedulerRunOutcome outcome, Pageable pageable);

    Page<SchedulerRun> findByTrigger(String trigger, Pageable pageable);

    Page<SchedulerRun> findByOutcome(SchedulerRunOutcome outcome, Pageable pageable);

    Optional<SchedulerRun> findFirstByOutcomeAndStartedAtAfterOrderByStartedAtDesc(
            SchedulerRunOutcome outcome,
            LocalDateTime startedAfter
    );

    Optional<SchedulerRun> findFirstByOutcomeNotOrderByStartedAtDesc(SchedulerRunOutcome outcome);
}
