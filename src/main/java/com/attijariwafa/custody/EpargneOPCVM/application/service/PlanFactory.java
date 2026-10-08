package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.CreatePlanRequest;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanSchedulePolicy;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PlanFactory {

    private final PlanSchedulePolicy schedulePolicy;

    public PlanFactory(PlanSchedulePolicy schedulePolicy) {
        this.schedulePolicy = schedulePolicy;
    }

    public SubscriptionPlan create(CreatePlanRequest request, String reference, LocalDate today) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference(reference);
        plan.setClientSecAccount(request.clientSecAccount());
        plan.setClientCashAccount(request.clientCashAccount());
        plan.setAssetCode(request.assetCode());
        plan.setByAmount(request.byAmount());
        plan.setAmount(request.recurringAmount());
        plan.setQuantity(request.recurringQuantity());
        plan.setFrequency(request.frequency());
        plan.setPreferredDayOfMonth(request.preferredDayOfMonth());
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setCurrentNominalCycleDate(schedulePolicy.calculateInitialNominalCycleDate(
                today,
                request.frequency(),
                request.preferredDayOfMonth()
        ));
        plan.setCurrentCycleDueDate(schedulePolicy.firstExecutableDate(plan.getCurrentNominalCycleDate()));
        plan.setNextExecutionDate(plan.getCurrentCycleDueDate());
        plan.setRetryDeadlineDate(schedulePolicy.calculateRetryDeadline(plan.getCurrentCycleDueDate()));
        return plan;
    }
}
