package com.attijariwafa.custody.EpargneOPCVM.application.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.UpdatePlanRequest;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.CreatePlanRequest;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.AccountsCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.CashBalancePort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OrderCreationPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.CashBalance;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmission;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmissionResult;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.DecimalizedFundThresholdPolicy;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanExecution;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanFrequency;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanSchedulePolicy;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.DuePlanLockRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.PlanExecutionRepository;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SubscriptionPlanRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
@ExtendWith(MockitoExtension.class)
class PlanExecutionServiceTest {
    // The service only executes plans on business days, so the tests pin the clock to a
    // Wednesday instead of using the machine clock (which made them fail on weekends).
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    @Mock
    private DuePlanLockRepository duePlanLockRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private PlanExecutionRepository planExecutionRepository;
    @Mock
    private CashBalancePort cashBalancePort;
    @Mock
    private OpcvmCatalogPort opcvmCatalogPort;
    @Mock
    private OrderCreationPort orderCreationPort;
    @Mock
    private AuditLogService auditLogService;
    private SimpleMeterRegistry meterRegistry;
    private NoOpTransactionManager transactionManager;
    @ParameterizedTest
    @CsvSource({
            "catalog, ASSET_NOT_FOUND",
            "catalog, EXTERNAL_SERVICE_FAILURE",
            "catalog, EXTERNAL_SERVICE_RATE_LIMITED",
            "balance, EXTERNAL_SERVICE_FAILURE",
            "balance, EXTERNAL_SERVICE_RATE_LIMITED",
            "order, EXTERNAL_SERVICE_FAILURE",
            "order, EXTERNAL_SERVICE_RATE_LIMITED"
    })
    void shouldRecordFailedAttemptAndContinueBatchWithValidPlan(String failureStage, ErrorCode errorCode) {
        SubscriptionPlan invalidPlan = basePlan();
        SubscriptionPlan validPlan = basePlan();
        validPlan.setId(2L);
        validPlan.setReference("EP000002");
        validPlan.setAssetCode("MA0000000002");
        validPlan.setClientCashAccount("1234567891");
        ApplicationException failure = new ApplicationException(errorCode, "First plan failed: " + errorCode);
        when(duePlanLockRepository.lockNextDuePlanId(TODAY))
                .thenReturn(Optional.of(1L), Optional.of(2L), Optional.empty());
        when(planRepository.findById(1L)).thenReturn(Optional.of(invalidPlan));
        when(planRepository.findById(2L)).thenReturn(Optional.of(validPlan));
        when(planExecutionRepository.countByPlanAndCycleDueDate(invalidPlan, TODAY)).thenReturn(1L);
        if (failureStage.equals("catalog")) {
            when(opcvmCatalogPort.getFund(invalidPlan.getAssetCode())).thenThrow(failure);
        } else {
            when(opcvmCatalogPort.getFund(invalidPlan.getAssetCode()))
                    .thenReturn(new FundDetails(invalidPlan.getAssetCode(), true, true, BigDecimal.TEN, null, null));
            if (failureStage.equals("balance")) {
                when(cashBalancePort.getBalance(invalidPlan.getClientCashAccount())).thenThrow(failure);
            } else {
                when(cashBalancePort.getBalance(invalidPlan.getClientCashAccount()))
                        .thenReturn(new CashBalance(invalidPlan.getClientCashAccount(), new BigDecimal("500")));
            }
        }
        when(opcvmCatalogPort.getFund(validPlan.getAssetCode()))
                .thenReturn(new FundDetails(validPlan.getAssetCode(), true, true, BigDecimal.TEN, null, null));
        when(cashBalancePort.getBalance(validPlan.getClientCashAccount()))
                .thenReturn(new CashBalance(validPlan.getClientCashAccount(), new BigDecimal("500")));
        OrderSubmissionResult success = new OrderSubmissionResult(true, "ORD-2", "accepted");
        if (failureStage.equals("order")) {
            when(orderCreationPort.createOrder(any())).thenThrow(failure).thenReturn(success);
        } else {
            when(orderCreationPort.createOrder(any())).thenReturn(success);
        }
        PlanExecutionService service = newService();
        DuePlanBatchResult result = service.executeDuePlans();
        assertThat(result.references()).containsExactly("EP000001", "EP000002");
        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.technicalFailureCount()).isEqualTo(1);
        assertThat(result.retriesScheduledCount()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.cyclesSkippedCount()).isZero();
        assertThat(result.batchExhausted()).isFalse();
        ArgumentCaptor<PlanExecution> executions = ArgumentCaptor.forClass(PlanExecution.class);
        verify(planExecutionRepository, times(2)).save(executions.capture());
        PlanExecution failedExecution = executions.getAllValues().get(0);
        assertThat(failedExecution.getPlan()).isSameAs(invalidPlan);
        assertThat(failedExecution.getResultStatus()).isEqualTo(ExecutionResultStatus.TECHNICAL_FAILURE);
        assertThat(failedExecution.getAttemptNumber()).isEqualTo(2);
        assertThat(failedExecution.getCycleDueDate()).isEqualTo(TODAY);
        assertThat(failedExecution.getExecutionKey()).isEqualTo("EP000001:" + TODAY);
        assertThat(failedExecution.getMessage()).isEqualTo(failure.getMessage());
        assertThat(invalidPlan.getLastAttemptDate()).isEqualTo(TODAY);
        assertThat(invalidPlan.getLastExecutionDate()).isNull();
        assertThat(invalidPlan.getNextExecutionDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(invalidPlan.getCurrentCycleDueDate()).isEqualTo(TODAY);
        assertThat(invalidPlan.getRetryDeadlineDate()).isEqualTo(TODAY.plusDays(7));
        assertThat(invalidPlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        verify(planRepository).save(invalidPlan);
        verify(auditLogService).log("PLAN_EXECUTION_RETRY_SCHEDULED", "PLAN", "EP000001", "FAILED",
                failedExecution, failure.getMessage());
        PlanExecution successfulExecution = executions.getAllValues().get(1);
        assertThat(successfulExecution.getPlan()).isSameAs(validPlan);
        assertThat(successfulExecution.getResultStatus()).isEqualTo(ExecutionResultStatus.SUCCESS);
        assertThat(successfulExecution.getExecutionKey()).isEqualTo("EP000002:" + TODAY);
        assertThat(successfulExecution.getExternalOrderReference()).isEqualTo("ORD-2");
        assertThat(validPlan.getLastExecutionDate()).isEqualTo(TODAY);
        assertThat(validPlan.getNextExecutionDate()).isAfter(TODAY);
        verify(planRepository).save(validPlan);
        assertThat(meterRegistry.counter("epargne_plan_retries_scheduled_total", "reason", "TECHNICAL_FAILURE").count())
                .isEqualTo(1);
        assertThat(transactionManager.commits).isEqualTo(3); // Two plans, then the empty scan.
        assertThat(transactionManager.rollbacks).isZero();
    }
    @Test
    void shouldScheduleRetryWhenFundsAreMissingBeforeDeadline() {
        SubscriptionPlan plan = basePlan();
        plan.setCurrentNominalCycleDate(TODAY);
        plan.setCurrentCycleDueDate(TODAY);
        plan.setNextExecutionDate(TODAY);
        plan.setRetryDeadlineDate(TODAY.plusDays(7));
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("10"), null, null));
        when(cashBalancePort.getBalance("1234567890")).thenReturn(new CashBalance("1234567890", new BigDecimal("50")));
        when(planExecutionRepository.countByPlanAndCycleDueDate(plan, TODAY)).thenReturn(0L);
        PlanExecutionService service = newService();
        DuePlanBatchResult.PlanOutcome outcome = service.executeNextDuePlan().orElseThrow();
        assertThat(outcome.reference()).isEqualTo("EP000001");
        assertThat(outcome.disposition()).isEqualTo(DuePlanBatchResult.Disposition.RETRY_SCHEDULED);
        assertThat(outcome.resultStatus()).isEqualTo(ExecutionResultStatus.INSUFFICIENT_FUNDS);
        assertThat(plan.getNextExecutionDate()).isAfter(TODAY);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        ArgumentCaptor<PlanExecution> executionCaptor = ArgumentCaptor.forClass(PlanExecution.class);
        verify(planExecutionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getResultStatus()).isEqualTo(ExecutionResultStatus.INSUFFICIENT_FUNDS);
        assertThat(meterRegistry.counter("epargne_plan_executions_total", "result", "INSUFFICIENT_FUNDS").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("epargne_plan_retries_scheduled_total", "reason", "INSUFFICIENT_FUNDS").count())
                .isEqualTo(1);
    }

    @Test
    void shouldExecuteNoneWhenCashCannotCoverAllDuePlansOnSameAccount() {
        SubscriptionPlan first = basePlan();
        first.setAmount(new BigDecimal("800"));
        SubscriptionPlan second = basePlan();
        second.setId(2L);
        second.setReference("EP000002");
        second.setAssetCode("MA0000000002");
        second.setAmount(new BigDecimal("800"));
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(first));
        when(duePlanLockRepository.lockDuePlansForCashAccount("1234567890", TODAY))
                .thenReturn(List.of(first, second));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("10"), null, null));
        when(opcvmCatalogPort.getFund("MA0000000002"))
                .thenReturn(new FundDetails("MA0000000002", true, true, new BigDecimal("10"), null, null));
        when(cashBalancePort.getBalance("1234567890")).thenReturn(new CashBalance("1234567890", new BigDecimal("1000")));
        when(planExecutionRepository.countByPlanAndCycleDueDate(first, TODAY)).thenReturn(0L);
        when(planExecutionRepository.countByPlanAndCycleDueDate(second, TODAY)).thenReturn(0L);
        newService().executeNextDuePlan();
        verify(orderCreationPort, never()).createOrder(any());
        ArgumentCaptor<PlanExecution> executions = ArgumentCaptor.forClass(PlanExecution.class);
        verify(planExecutionRepository, times(2)).save(executions.capture());
        assertThat(executions.getAllValues())
                .allMatch(execution -> execution.getResultStatus() == ExecutionResultStatus.INSUFFICIENT_FUNDS);
        assertThat(first.getNextExecutionDate()).isAfter(TODAY);
        assertThat(second.getNextExecutionDate()).isAfter(TODAY);
    }

    @Test
    void shouldSkipCycleAndKeepPlanActiveWhenRetryWindowIsExhausted() {
        LocalDate cycleDueDate = LocalDate.of(2026, 7, 5);
        SubscriptionPlan plan = basePlan();
        plan.setCurrentNominalCycleDate(cycleDueDate);
        plan.setCurrentCycleDueDate(cycleDueDate);
        plan.setNextExecutionDate(TODAY);
        plan.setRetryDeadlineDate(TODAY);
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("10"), null, null));
        when(cashBalancePort.getBalance("1234567890")).thenReturn(new CashBalance("1234567890", new BigDecimal("50")));
        when(planExecutionRepository.countByPlanAndCycleDueDate(plan, cycleDueDate)).thenReturn(2L);
        PlanExecutionService service = newService();
        DuePlanBatchResult.PlanOutcome outcome = service.executeNextDuePlan().orElseThrow();
        assertThat(outcome.disposition()).isEqualTo(DuePlanBatchResult.Disposition.CYCLE_SKIPPED);
        assertThat(outcome.resultStatus()).isEqualTo(ExecutionResultStatus.INSUFFICIENT_FUNDS);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.getCurrentCycleDueDate()).isAfter(cycleDueDate);
        assertThat(plan.getNextExecutionDate()).isEqualTo(plan.getCurrentCycleDueDate());
        assertThat(plan.getLastExecutionDate()).isNull();
        assertThat(meterRegistry.counter("epargne_plan_cycles_skipped_total", "reason", "INSUFFICIENT_FUNDS").count())
                .isEqualTo(1);
    }
    @Test
    void shouldScheduleRetryWhenInvestmentIsBelowVlThreshold() {
        SubscriptionPlan plan = basePlan();
        plan.setAmount(new BigDecimal("500"));
        plan.setCurrentNominalCycleDate(TODAY);
        plan.setCurrentCycleDueDate(TODAY);
        plan.setNextExecutionDate(TODAY);
        plan.setRetryDeadlineDate(TODAY.plusDays(7));
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("620000"), null, null));
        when(planExecutionRepository.countByPlanAndCycleDueDate(plan, TODAY)).thenReturn(0L);
        PlanExecutionService service = newService();
        DuePlanBatchResult.PlanOutcome outcome = service.executeNextDuePlan().orElseThrow();
        assertThat(outcome.reference()).isEqualTo("EP000001");
        assertThat(outcome.resultStatus()).isEqualTo(ExecutionResultStatus.AMOUNT_BELOW_VL_THRESHOLD);
        assertThat(plan.getNextExecutionDate()).isAfter(TODAY);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        ArgumentCaptor<PlanExecution> executionCaptor = ArgumentCaptor.forClass(PlanExecution.class);
        verify(planExecutionRepository).save(executionCaptor.capture());
        assertThat(executionCaptor.getValue().getResultStatus()).isEqualTo(ExecutionResultStatus.AMOUNT_BELOW_VL_THRESHOLD);
        verify(cashBalancePort, never()).getBalance(any());
        assertThat(meterRegistry.counter("epargne_plan_retries_scheduled_total", "reason", "AMOUNT_BELOW_VL_THRESHOLD").count())
                .isEqualTo(1);
    }
    @Test
    void shouldAdvancePlanOnSuccessfulOrder() {
        SubscriptionPlan plan = basePlan();
        plan.setCurrentNominalCycleDate(TODAY);
        plan.setCurrentCycleDueDate(TODAY);
        plan.setNextExecutionDate(TODAY);
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("10"), null, null));
        when(cashBalancePort.getBalance("1234567890")).thenReturn(new CashBalance("1234567890", new BigDecimal("500")));
        when(planExecutionRepository.countByPlanAndCycleDueDate(plan, TODAY)).thenReturn(0L);
        when(planExecutionRepository.findFirstByExecutionKeyAndResultStatusOrderByExecutedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(orderCreationPort.createOrder(any())).thenReturn(new OrderSubmissionResult(true, "ORD-1", "accepted"));
        PlanExecutionService service = newService();
        DuePlanBatchResult.PlanOutcome outcome = service.executeNextDuePlan().orElseThrow();
        assertThat(outcome.disposition()).isEqualTo(DuePlanBatchResult.Disposition.EXECUTED);
        assertThat(outcome.resultStatus()).isEqualTo(ExecutionResultStatus.SUCCESS);
        assertThat(plan.getLastExecutionDate()).isEqualTo(TODAY);
        assertThat(plan.getCurrentCycleDueDate()).isAfter(TODAY);
        assertThat(plan.getNextExecutionDate()).isEqualTo(plan.getCurrentCycleDueDate());
        assertThat(meterRegistry.counter("epargne_plan_executions_total", "result", "SUCCESS").count())
                .isEqualTo(1);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUsePatchedScheduleAfterDueCycleSucceedsOrIsSkipped(boolean succeeds) {
        SubscriptionPlan plan = basePlan();
        LocalDate dueDate = LocalDate.of(2026, 8, 5);
        plan.setCurrentNominalCycleDate(dueDate);
        plan.setCurrentCycleDueDate(dueDate);
        plan.setRetryDeadlineDate(TODAY);
        when(planRepository.findByReference(plan.getReference())).thenReturn(Optional.of(plan));
        when(planRepository.save(plan)).thenReturn(plan);
        when(opcvmCatalogPort.getFund(plan.getAssetCode()))
                .thenReturn(new FundDetails(plan.getAssetCode(), true, true, BigDecimal.TEN, null, null));
        PlanSchedulePolicy schedulePolicy = new PlanSchedulePolicy();
        PlanManagementService managementService = new PlanManagementService(
                planRepository, mock(AccountsCatalogPort.class), opcvmCatalogPort,
                new PlanFactory(schedulePolicy), schedulePolicy, auditLogService, FIXED_CLOCK);
        managementService.updatePlan(plan.getReference(), new UpdatePlanRequest(
                null, null, null, null, null, PlanFrequency.QUARTERLY, 20));
        assertThat(plan.getCurrentCycleDueDate()).isEqualTo(dueDate);
        assertThat(plan.getNextExecutionDate()).isEqualTo(TODAY);
        assertThat(plan.getRetryDeadlineDate()).isEqualTo(TODAY);
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(cashBalancePort.getBalance(plan.getClientCashAccount()))
                .thenReturn(new CashBalance(plan.getClientCashAccount(), succeeds ? new BigDecimal("500") : BigDecimal.ZERO));
        if (succeeds) {
            when(orderCreationPort.createOrder(any())).thenReturn(new OrderSubmissionResult(true, "ORD-1", "accepted"));
        }
        newService().executeNextDuePlan();
        ArgumentCaptor<PlanExecution> execution = ArgumentCaptor.forClass(PlanExecution.class);
        verify(planExecutionRepository).save(execution.capture());
        assertThat(execution.getValue().getCycleDueDate()).isEqualTo(dueDate);
        assertThat(execution.getValue().getExecutionKey()).isEqualTo("EP000001:2026-08-05");
        assertThat(execution.getValue().getResultStatus()).isEqualTo(
                succeeds ? ExecutionResultStatus.SUCCESS : ExecutionResultStatus.INSUFFICIENT_FUNDS);
        if (succeeds) {
            ArgumentCaptor<OrderSubmission> order = ArgumentCaptor.forClass(OrderSubmission.class);
            verify(orderCreationPort).createOrder(order.capture());
            assertThat(order.getValue().executionKey()).isEqualTo("EP000001:2026-08-05");
        } else {
            verify(orderCreationPort, never()).createOrder(any());
        }
        assertThat(plan.getCurrentCycleDueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(plan.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(plan.getRetryDeadlineDate()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }
    @ParameterizedTest
    @CsvSource({
            "MONTHLY, success, 2026-02-02, 2026-02-28, 2026-03-02",
            "MONTHLY, skip, 2026-02-09, 2026-02-28, 2026-03-02",
            "MONTHLY, duplicate, 2026-02-02, 2026-02-28, 2026-03-02",
            "MONTHLY, retry, 2026-02-03, 2026-02-28, 2026-03-02",
            "QUARTERLY, success, 2026-02-02, 2026-04-30, 2026-04-30",
            "QUARTERLY, skip, 2026-02-09, 2026-04-30, 2026-04-30"
    })
    void shouldKeepNominalAnchorWhenAdvancingRolledOverCycle(PlanFrequency frequency, String outcome,
                                                             LocalDate executionDate, LocalDate nextNominal,
                                                             LocalDate nextDue) {
        PlanSchedulePolicy policy = new PlanSchedulePolicy();
        SubscriptionPlan plan = new PlanFactory(policy).create(new CreatePlanRequest(
                "0000000001", "1234567890", "MA0000000001", true,
                new BigDecimal("100"), null, frequency, 31), "EP000001", LocalDate.of(2026, 1, 1));
        plan.setId(1L);
        assertThat(plan.getCurrentNominalCycleDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(plan.getCurrentCycleDueDate()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(plan.getRetryDeadlineDate()).isEqualTo(LocalDate.of(2026, 2, 9));
        when(duePlanLockRepository.lockNextDuePlanId(executionDate)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund(plan.getAssetCode()))
                .thenReturn(new FundDetails(plan.getAssetCode(), true, true, BigDecimal.TEN, null, null));
        when(cashBalancePort.getBalance(plan.getClientCashAccount()))
                .thenReturn(new CashBalance(plan.getClientCashAccount(),
                        outcome.equals("skip") ? BigDecimal.ZERO : new BigDecimal("500")));
        if (outcome.equals("duplicate")) {
            when(planExecutionRepository.findFirstByExecutionKeyAndResultStatusOrderByExecutedAtDesc(
                    "EP000001:2026-02-02", ExecutionResultStatus.SUCCESS)).thenReturn(Optional.of(new PlanExecution()));
        } else if (!outcome.equals("skip")) {
            when(orderCreationPort.createOrder(any())).thenReturn(new OrderSubmissionResult(true, "ORD-1", "accepted"));
        }
        if (outcome.equals("retry")) {
            plan.setNextExecutionDate(executionDate);
            plan.setLastAttemptDate(executionDate.minusDays(1));
        }
        newService(Clock.fixed(executionDate.atStartOfDay(FIXED_CLOCK.getZone()).toInstant(), FIXED_CLOCK.getZone()))
                .executeNextDuePlan();
        assertThat(plan.getCurrentNominalCycleDate()).isEqualTo(nextNominal);
        assertThat(plan.getCurrentCycleDueDate()).isEqualTo(nextDue);
        assertThat(plan.getNextExecutionDate()).isEqualTo(nextDue);
        assertThat(plan.getRetryDeadlineDate()).isEqualTo(policy.calculateRetryDeadline(nextDue));
        if (outcome.equals("duplicate") || outcome.equals("skip")) {
            verify(orderCreationPort, never()).createOrder(any());
        } else {
            ArgumentCaptor<OrderSubmission> order = ArgumentCaptor.forClass(OrderSubmission.class);
            verify(orderCreationPort).createOrder(order.capture());
            assertThat(order.getValue().executionKey()).isEqualTo("EP000001:2026-02-02");
        }
    }
    @Test
    void shouldDeferPlanWithoutAttemptOnNonBusinessDay() {
        LocalDate saturday = LocalDate.of(2026, 8, 15);
        SubscriptionPlan plan = basePlan();
        plan.setNextExecutionDate(saturday);
        when(duePlanLockRepository.lockNextDuePlanId(saturday)).thenReturn(Optional.of(1L), Optional.empty());
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        DuePlanBatchResult result = newService(
                Clock.fixed(saturday.atStartOfDay(FIXED_CLOCK.getZone()).toInstant(), FIXED_CLOCK.getZone()))
                .executeDuePlans();
        assertThat(result.references()).containsExactly("EP000001");
        assertThat(result.nonBusinessDayDeferredCount()).isEqualTo(1);
        assertThat(result.outcomes().getFirst().disposition())
                .isEqualTo(DuePlanBatchResult.Disposition.NON_BUSINESS_DAY_DEFERRED);
        assertThat(result.outcomes().getFirst().resultStatus()).isNull();
        assertThat(plan.getNextExecutionDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        verify(planExecutionRepository, never()).save(any());
        verify(opcvmCatalogPort, never()).getFund(any());
    }
    @Test
    void shouldCancelDuePlanInsteadOfOrderingWhenFundIsIneligible() {
        SubscriptionPlan plan = basePlan();
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planExecutionRepository.countByPlanAndCycleDueDate(plan, TODAY)).thenReturn(0L);
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, false, new BigDecimal("10"), null, null));
        Optional<DuePlanBatchResult.PlanOutcome> outcome = newService().executeNextDuePlan();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThat(outcome).contains(new DuePlanBatchResult.PlanOutcome("EP000001", DuePlanBatchResult.Disposition.CANCELLED, null));
        verify(orderCreationPort, never()).createOrder(any());
        verify(cashBalancePort, never()).getBalance(any());
    }
    @Test
    void shouldReturnEmptyWhenNothingIsDue() {
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.empty());
        DuePlanBatchResult result = newService().executeDuePlans();
        assertThat(result.processedCount()).isZero();
        assertThat(result.references()).isEmpty();
        assertThat(transactionManager.commits).isEqualTo(1);
    }
    @Test
    void shouldStopAtBatchSizeAndReportExhaustion() {
        // Every claim yields a fresh due plan, as if the table held more than the batch size.
        when(duePlanLockRepository.lockNextDuePlanId(TODAY)).thenReturn(Optional.of(1L));
        when(planRepository.findById(1L)).thenAnswer(invocation -> Optional.of(basePlan()));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("10"), null, null));
        when(cashBalancePort.getBalance("1234567890")).thenReturn(new CashBalance("1234567890", new BigDecimal("500")));
        when(planExecutionRepository.findFirstByExecutionKeyAndResultStatusOrderByExecutedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(orderCreationPort.createOrder(any())).thenReturn(new OrderSubmissionResult(true, "ORD-1", "accepted"));
        DuePlanBatchResult result = newService().executeDuePlans();
        assertThat(result.processedCount()).isEqualTo(10);
        assertThat(result.batchExhausted()).isTrue();
        assertThat(result.successCount()).isEqualTo(10);
    }
    @Test
    void shouldWrapLoopBreakWithPartialResult() {
        SubscriptionPlan plan = basePlan();
        when(duePlanLockRepository.lockNextDuePlanId(TODAY))
                .thenReturn(Optional.of(1L))
                .thenThrow(new IllegalStateException("connection lost"));
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund("MA0000000001"))
                .thenReturn(new FundDetails("MA0000000001", true, true, new BigDecimal("10"), null, null));
        when(cashBalancePort.getBalance("1234567890")).thenReturn(new CashBalance("1234567890", new BigDecimal("500")));
        when(planExecutionRepository.findFirstByExecutionKeyAndResultStatusOrderByExecutedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(orderCreationPort.createOrder(any())).thenReturn(new OrderSubmissionResult(true, "ORD-1", "accepted"));
        PlanExecutionService service = newService();
        DuePlanBatchFailedException failure = assertThrows(DuePlanBatchFailedException.class, service::executeDuePlans);
        assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
        assertThat(failure.partialResult().references()).containsExactly("EP000001");
        assertThat(failure.partialResult().successCount()).isEqualTo(1);
    }
    private PlanExecutionService newService() {
        return newService(FIXED_CLOCK);
    }
    private PlanExecutionService newService(Clock clock) {
        SchedulerProperties schedulerProperties = new SchedulerProperties();
        schedulerProperties.setBatchSize(10);
        meterRegistry = new SimpleMeterRegistry();
        transactionManager = new NoOpTransactionManager();
        return new PlanExecutionService(
                duePlanLockRepository,
                planRepository,
                planExecutionRepository,
                cashBalancePort,
                opcvmCatalogPort,
                orderCreationPort,
                new PlanSchedulePolicy(),
                new DecimalizedFundThresholdPolicy(),
                auditLogService,
                schedulerProperties,
                new PlanMetrics(meterRegistry),
                transactionManager,
                clock
        );
    }
    private SubscriptionPlan basePlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(1L);
        plan.setReference("EP000001");
        plan.setClientSecAccount("0000000001");
        plan.setClientCashAccount("1234567890");
        plan.setAssetCode("MA0000000001");
        plan.setByAmount(true);
        plan.setAmount(new BigDecimal("100"));
        plan.setFrequency(PlanFrequency.MONTHLY);
        plan.setPreferredDayOfMonth(5);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setCurrentNominalCycleDate(TODAY);
        plan.setCurrentCycleDueDate(TODAY);
        plan.setNextExecutionDate(TODAY);
        plan.setRetryDeadlineDate(TODAY.plusDays(7));
        return plan;
    }
    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        private int commits;
        private int rollbacks;
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }
        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }
        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}