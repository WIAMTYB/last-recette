package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.CashBalancePort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OrderCreationPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.CashBalance;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmission;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmissionResult;
import com.attijariwafa.custody.EpargneOPCVM.application.service.DuePlanBatchResult.Disposition;
import com.attijariwafa.custody.EpargneOPCVM.application.service.DuePlanBatchResult.PlanOutcome;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.DecimalizedFundThresholdPolicy;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanExecution;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanSchedulePolicy;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.DuePlanLockRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.PlanExecutionRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SubscriptionPlanRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanExecutionService.class);

    private final DuePlanLockRepository duePlanLockRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PlanExecutionRepository planExecutionRepository;
    private final CashBalancePort cashBalancePort;
    private final OpcvmCatalogPort opcvmCatalogPort;
    private final OrderCreationPort orderCreationPort;
    private final PlanSchedulePolicy schedulePolicy;
    private final DecimalizedFundThresholdPolicy fundThresholdPolicy;
    private final AuditLogService auditLogService;
    private final SchedulerProperties schedulerProperties;
    private final PlanMetrics planMetrics;
    private final TransactionTemplate requiresNewTransaction;
    private final Clock clock;

    public PlanExecutionService(DuePlanLockRepository duePlanLockRepository,
                                SubscriptionPlanRepository planRepository,
                                PlanExecutionRepository planExecutionRepository,
                                CashBalancePort cashBalancePort,
                                OpcvmCatalogPort opcvmCatalogPort,
                                OrderCreationPort orderCreationPort,
                                PlanSchedulePolicy schedulePolicy,
                                DecimalizedFundThresholdPolicy fundThresholdPolicy,
                                AuditLogService auditLogService,
                                SchedulerProperties schedulerProperties,
                                PlanMetrics planMetrics,
                                PlatformTransactionManager transactionManager,
                                Clock clock) {
        this.duePlanLockRepository = duePlanLockRepository;
        this.planRepository = planRepository;
        this.planExecutionRepository = planExecutionRepository;
        this.cashBalancePort = cashBalancePort;
        this.opcvmCatalogPort = opcvmCatalogPort;
        this.orderCreationPort = orderCreationPort;
        this.schedulePolicy = schedulePolicy;
        this.fundThresholdPolicy = fundThresholdPolicy;
        this.auditLogService = auditLogService;
        this.schedulerProperties = schedulerProperties;
        this.planMetrics = planMetrics;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Claims and processes due plans one at a time, up to the configured batch size.
     * Expected business failures are recorded per plan and never stop the loop; a break
     * of the loop itself surfaces as {@link DuePlanBatchFailedException} carrying the
     * plans already processed.
     */
    public DuePlanBatchResult executeDuePlans() {
        cancelPlansWithIneligibleFunds();
        DuePlanBatchResult result = new DuePlanBatchResult(schedulerProperties.getBatchSize());
        try {
            for (int i = 0; i < schedulerProperties.getBatchSize(); i++) {
                Optional<PlanOutcome> outcome = executeNextDuePlan();
                if (outcome.isEmpty()) {
                    break;
                }
                result.record(outcome.get());
            }
        } catch (RuntimeException ex) {
            throw new DuePlanBatchFailedException(result, ex);
        }
        return result;
    }

    /** Empty when no plan is due (or every due plan is locked by another worker). */
    public Optional<PlanOutcome> executeNextDuePlan() {
        return Optional.ofNullable(requiresNewTransaction.execute(status -> executeNextDuePlanInTransaction()));
    }

    private PlanOutcome executeNextDuePlanInTransaction() {
        LocalDate today = LocalDate.now(clock);
        Long planId = duePlanLockRepository.lockNextDuePlanId(today).orElse(null);
        if (planId == null) {
            return null;
        }

        SubscriptionPlan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            return null;
        }

        if (plan.getStatus() != PlanStatus.ACTIVE || plan.getNextExecutionDate().isAfter(today)) {
            return new PlanOutcome(plan.getReference(), Disposition.NOT_DUE, null);
        }

        if (!schedulePolicy.isBusinessDay(today)) {
            plan.setNextExecutionDate(schedulePolicy.calculateNextRetryDate(today));
            planRepository.save(plan);
            return new PlanOutcome(plan.getReference(), Disposition.NON_BUSINESS_DAY_DEFERRED, null);
        }

        int attemptNumber = (int) planExecutionRepository.countByPlanAndCycleDueDate(plan, plan.getCurrentCycleDueDate()) + 1;
        String executionKey = plan.getReference() + ":" + plan.getCurrentCycleDueDate();

        FundDetails fundDetails;
        try {
            fundDetails = opcvmCatalogPort.getFund(plan.getAssetCode());
        } catch (ApplicationException ex) {
            return handleFailure(plan, today, attemptNumber, executionKey, ExecutionResultStatus.TECHNICAL_FAILURE, ex.getMessage());
        }
        if (!isEligibleForScheduledSavings(fundDetails)){
            cancelPlanForIneligibleFund(plan);
            return new PlanOutcome(plan.getReference(),Disposition.CANCELLED,null);
        }
        BigDecimal investmentAmount = plan.isByAmount()
                ? plan.getAmount()
                : fundDetails.latestNav().multiply(plan.getQuantity());

        if (!fundThresholdPolicy.isInvestmentSufficient(investmentAmount, fundDetails.latestNav())) {
            return handleFailure(
                    plan,
                    today,
                    attemptNumber,
                    executionKey,
                    ExecutionResultStatus.AMOUNT_BELOW_VL_THRESHOLD,
                    fundThresholdPolicy.belowThresholdMessage(investmentAmount, fundDetails.latestNav()));
        }


        List<SubscriptionPlan> basket = duePlanLockRepository.lockDuePlansForCashAccount(
                plan.getClientCashAccount(), today);
        if (basket == null || basket.isEmpty()) {
            basket = List.of(plan);
        }

        Map<String, FundDetails> fundsByAsset = new HashMap<>();
        fundsByAsset.put(plan.getAssetCode(), fundDetails);
        BigDecimal basketRequired;
        try {
            basketRequired = requiredCashForBasket(basket, fundsByAsset);
        } catch (ApplicationException ex) {
            return handleFailure(plan, today, attemptNumber, executionKey, ExecutionResultStatus.TECHNICAL_FAILURE, ex.getMessage());
        }

        CashBalance balance;
        try {
            balance = cashBalancePort.getBalance(plan.getClientCashAccount());
        } catch (ApplicationException ex) {
            return handleFailure(plan, today, attemptNumber, executionKey, ExecutionResultStatus.TECHNICAL_FAILURE, ex.getMessage());
        }
        BigDecimal availableAmount = balance.availableAmount();

        if (availableAmount.compareTo(basketRequired) < 0) {
            return failBasketInsufficientFunds(basket, today);
        }

        if (planExecutionRepository.findFirstByExecutionKeyAndResultStatusOrderByExecutedAtDesc(executionKey, ExecutionResultStatus.SUCCESS).isPresent()) {
            advancePlanAfterSuccess(plan, today);
            planRepository.save(plan);
            planMetrics.recordPlanExecution(ExecutionResultStatus.SUCCESS);
            return new PlanOutcome(plan.getReference(), Disposition.EXECUTED, ExecutionResultStatus.SUCCESS);
        }

        try {
            OrderSubmissionResult result = orderCreationPort.createOrder(new OrderSubmission(
                    plan.getReference(),
                    executionKey,
                    plan.getCurrentCycleDueDate(),
                    plan.getClientSecAccount(),
                    plan.getClientCashAccount(),
                    plan.getAssetCode(),
                    plan.isByAmount(),
                    plan.getAmount(),
                    plan.getQuantity()
            ));

            if (!result.accepted()) {
                return handleFailure(plan, today, attemptNumber, executionKey, ExecutionResultStatus.ORDER_REJECTED, result.message());
            }

            PlanExecution execution = new PlanExecution();
            execution.setPlan(plan);
            execution.setExecutedAt(LocalDateTime.now(clock));
            execution.setCycleDueDate(plan.getCurrentCycleDueDate());
            execution.setAttemptNumber(attemptNumber);
            execution.setResultStatus(ExecutionResultStatus.SUCCESS);
            execution.setExecutionKey(executionKey);
            execution.setExternalOrderReference(result.orderReference());
            execution.setMessage(result.message());
            planExecutionRepository.save(execution);

            advancePlanAfterSuccess(plan, today);
            planRepository.save(plan);
            planMetrics.recordPlanExecution(ExecutionResultStatus.SUCCESS);
            auditLogService.log("PLAN_EXECUTION_SUCCEEDED", "PLAN", plan.getReference(), "SUCCESS", execution, null);
            return new PlanOutcome(plan.getReference(), Disposition.EXECUTED, ExecutionResultStatus.SUCCESS);
        } catch (RuntimeException ex) {
            LOGGER.error("Order creation failed for {}", plan.getReference(), ex);
            return handleFailure(plan, today, attemptNumber, executionKey, ExecutionResultStatus.TECHNICAL_FAILURE, ex.getMessage());
        }
    }

    private void cancelPlansWithIneligibleFunds() {
        Map<String, FundDetails> funds;
        try {
            funds = opcvmCatalogPort.listFundsByIsin();
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to load OPCVM catalog before cancelling ineligible plans", ex);
            return;
        }
        List<SubscriptionPlan> openPlans;
        try {
            openPlans = planRepository.findByStatusNot(PlanStatus.CANCELLED);
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to list plans before cancelling ineligible funds", ex);
            return;
        }
        if (openPlans == null || openPlans.isEmpty()) {
            return;
        }
        for (SubscriptionPlan plan : openPlans) {
            if (isEligibleForScheduledSavings(funds.get(plan.getAssetCode()))) {
                continue;
            }
            requiresNewTransaction.execute(status -> {
                SubscriptionPlan current = planRepository.findById(plan.getId()).orElse(null);
                if (current == null || current.getStatus() == PlanStatus.CANCELLED) {
                    return null;
                }
                cancelPlanForIneligibleFund(current);
                return null;
            });
        }
    }
    private void cancelPlanForIneligibleFund(SubscriptionPlan plan) {
        plan.setStatus(PlanStatus.CANCELLED);
        planRepository.save(plan);
        auditLogService.log(
                "PLAN_CANCELLED",
                "PLAN",
                plan.getReference(),
                "SUCCESS",
                plan,
                "OPCVM is no longer eligible for scheduled savings"
        );
    }
    private static boolean isEligibleForScheduledSavings(FundDetails fund) {
        return fund != null && fund.active() && fund.acceptedForScheduledSavings();
    }


    private BigDecimal requiredCashForBasket(List<SubscriptionPlan> basket, Map<String, FundDetails> fundsByAsset) {
        BigDecimal total = BigDecimal.ZERO;
        for (SubscriptionPlan duePlan : basket) {
            FundDetails fund = fundsByAsset.get(duePlan.getAssetCode());
            if (fund == null) {
                fund = opcvmCatalogPort.getFund(duePlan.getAssetCode());
                fundsByAsset.put(duePlan.getAssetCode(), fund);
            }
            BigDecimal amount = duePlan.isByAmount()
                    ? duePlan.getAmount()
                    : fund.latestNav().multiply(duePlan.getQuantity());
            total = total.add(amount);
        }
        return total;
    }
    private PlanOutcome failBasketInsufficientFunds(List<SubscriptionPlan> basket, LocalDate today) {
        PlanOutcome lastOutcome = null;
        String message = "Insufficient funds for all due plans on this cash account";
        for (SubscriptionPlan duePlan : basket) {
            int attemptNumber = (int) planExecutionRepository.countByPlanAndCycleDueDate(
                    duePlan, duePlan.getCurrentCycleDueDate()) + 1;
            String executionKey = duePlan.getReference() + ":" + duePlan.getCurrentCycleDueDate();
            lastOutcome = handleFailure(
                    duePlan,
                    today,
                    attemptNumber,
                    executionKey,
                    ExecutionResultStatus.INSUFFICIENT_FUNDS,
                    message);
        }
        return lastOutcome;
    }



    private PlanOutcome handleFailure(SubscriptionPlan plan, LocalDate today, int attemptNumber, String executionKey,
                                      ExecutionResultStatus resultStatus, String message) {
        PlanExecution execution = new PlanExecution();
        execution.setPlan(plan);
        execution.setExecutedAt(LocalDateTime.now(clock));
        execution.setCycleDueDate(plan.getCurrentCycleDueDate());
        execution.setAttemptNumber(attemptNumber);
        execution.setResultStatus(resultStatus);
        execution.setExecutionKey(executionKey);
        execution.setMessage(message);
        planExecutionRepository.save(execution);
        planMetrics.recordPlanExecution(resultStatus);

        plan.setLastAttemptDate(today);
        if (!today.isBefore(plan.getRetryDeadlineDate())) {
            advancePlanAfterSkippedCycle(plan, today);
            planRepository.save(plan);
            planMetrics.recordPlanCycleSkipped(resultStatus.name());
            auditLogService.log("PLAN_CYCLE_SKIPPED", "PLAN", plan.getReference(), "FAILED", execution, message);
            return new PlanOutcome(plan.getReference(), Disposition.CYCLE_SKIPPED, resultStatus);
        }

        plan.setNextExecutionDate(schedulePolicy.calculateNextRetryDate(today));
        planRepository.save(plan);
        planMetrics.recordPlanRetryScheduled(resultStatus.name());
        auditLogService.log("PLAN_EXECUTION_RETRY_SCHEDULED", "PLAN", plan.getReference(), "FAILED", execution, message);
        return new PlanOutcome(plan.getReference(), Disposition.RETRY_SCHEDULED, resultStatus);
    }

    private void advancePlanAfterSuccess(SubscriptionPlan plan, LocalDate today) {
        plan.setLastExecutionDate(today);
        advancePlanCycle(plan, today);
    }

    private void advancePlanAfterSkippedCycle(SubscriptionPlan plan, LocalDate today) {
        advancePlanCycle(plan, today);
    }

    private void advancePlanCycle(SubscriptionPlan plan, LocalDate today) {
        LocalDate nextNominalCycle = schedulePolicy.calculateNextNominalCycleDate(
                plan.getCurrentNominalCycleDate(),
                plan.getFrequency(),
                plan.getPreferredDayOfMonth()
        );
        LocalDate nextCycle = schedulePolicy.firstExecutableDate(nextNominalCycle);
        plan.setLastAttemptDate(today);
        plan.setCurrentNominalCycleDate(nextNominalCycle);
        plan.setCurrentCycleDueDate(nextCycle);
        plan.setNextExecutionDate(nextCycle);
        plan.setRetryDeadlineDate(schedulePolicy.calculateRetryDeadline(nextCycle));
    }
}
