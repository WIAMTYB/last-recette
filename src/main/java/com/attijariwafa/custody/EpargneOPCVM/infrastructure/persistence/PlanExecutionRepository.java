package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanExecution;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanExecutionRepository extends JpaRepository<PlanExecution, Long> {

    List<PlanExecution> findByPlanReferenceOrderByExecutedAtDesc(String reference);

    long countByPlanAndCycleDueDate(SubscriptionPlan plan, LocalDate cycleDueDate);

    Optional<PlanExecution> findFirstByExecutionKeyAndResultStatusOrderByExecutedAtDesc(
            String executionKey,
            com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus resultStatus
    );
}
