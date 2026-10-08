package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.PlanExistsResponse;
import com.attijariwafa.custody.EpargneOPCVM.domain.audit.AuditEvent;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanExecution;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.AuditEventRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.PlanExecutionRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SubscriptionPlanRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SubscriptionPlanSpecifications;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PlanQueryService {

    public record PlanStats(Map<PlanStatus, Long> byStatus, long dueToday, long inRetry) {
    }

    private static final List<String> CLIENT_HISTORY_EVENT_TYPES = List.of(
            "PLAN_CREATED",
            "PLAN_UPDATED",
            "PLAN_PAUSED",
            "PLAN_SUSPENDED",
            "PLAN_RESUMED",
            "PLAN_CANCELLED",
            "PLAN_EXECUTION_SUCCEEDED",
            "PLAN_EXECUTION_RETRY_SCHEDULED",
            "PLAN_CYCLE_SKIPPED"
    );

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PlanExecutionRepository planExecutionRepository;
    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public PlanQueryService(SubscriptionPlanRepository subscriptionPlanRepository,
                            PlanExecutionRepository planExecutionRepository,
                            AuditEventRepository auditEventRepository,
                            Clock clock) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.planExecutionRepository = planExecutionRepository;
        this.auditEventRepository =auditEventRepository;
        this.clock = clock;
    }

    public SubscriptionPlan getPlan(String reference) {
        return subscriptionPlanRepository.findByReference(reference)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + reference));
    }

    public Page<SubscriptionPlan> listPlans(String secAccount, PlanStatus status, int page, int size) {
        return listPlans(PlanSearchCriteria.of(secAccount, status, page, size));
    }

    public Page<SubscriptionPlan> listPlans(PlanSearchCriteria criteria) {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), criteria.sort());
        return subscriptionPlanRepository.findAll(
                SubscriptionPlanSpecifications.matching(criteria, LocalDate.now(clock)),
                pageable);
    }

    public PlanExistsResponse findExistingPlan(String secAccount, String assetCode) {
        return subscriptionPlanRepository
                .findFirstByClientSecAccountAndAssetCodeAndStatusNot(
                        secAccount, assetCode, PlanStatus.CANCELLED)
                .map(plan -> new PlanExistsResponse(true, plan.getReference(), plan.getStatus(),"An active plan already exists for this fund"))
                .orElseGet(() -> new PlanExistsResponse(false, null, null,null ));
    }

    public PlanStats stats() {
        Map<PlanStatus, Long> byStatus = new EnumMap<>(PlanStatus.class);
        for (PlanStatus status : PlanStatus.values()) {
            byStatus.put(status, subscriptionPlanRepository.countByStatus(status));
        }
        LocalDate today = LocalDate.now(clock);
        return new PlanStats(
                byStatus,
                subscriptionPlanRepository.countByStatusAndNextExecutionDateLessThanEqual(PlanStatus.ACTIVE, today),
                subscriptionPlanRepository.countInRetry(PlanStatus.ACTIVE));
    }

    public List<PlanExecution> listExecutions(String reference) {
        if (!subscriptionPlanRepository.existsByReference(reference)) {
            throw new ApplicationException(ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + reference);
        }
        return planExecutionRepository.findByPlanReferenceOrderByExecutedAtDesc(reference);
    }

    public List<AuditEvent> listHistory(String reference) {
        if (!subscriptionPlanRepository.existsByReference(reference)) {
            throw new ApplicationException(ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + reference);
        }
        return auditEventRepository.findByEntityTypeAndEntityReferenceAndEventTypeInOrderByEventTimeDesc(
                "PLAN",
                reference,
                CLIENT_HISTORY_EVENT_TYPES
        );
    }
}
