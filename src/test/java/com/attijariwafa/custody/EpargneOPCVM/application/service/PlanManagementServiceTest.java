package com.attijariwafa.custody.EpargneOPCVM.application.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.CreatePlanRequest;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.PlanFieldChange;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.UpdatePlanRequest;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.AccountsCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.AccountRelationship;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanFrequency;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanSchedulePolicy;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence.SubscriptionPlanRepository;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class PlanManagementServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 12);
    private static final Clock FIXED_CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private AccountsCatalogPort accountsCatalogPort;
    @Mock
    private OpcvmCatalogPort opcvmCatalogPort;
    @Mock
    private AuditLogService auditLogService;
    @ParameterizedTest
    @CsvSource({
            "1, EP000001",
            "42, EP000042",
            "1000000, EP1000000",
            "999999999999999999, EP999999999999999999"
    })
    void shouldCreateAndAuditPlanWithAllocatedReference(long sequenceValue, String expectedReference) {
        when(accountsCatalogPort.findRelationship("0000000001", "1234567890"))
                .thenReturn(new AccountRelationship("0000000001", "1234567890", true));
        when(opcvmCatalogPort.getFund("MA0000030520"))
                .thenReturn(new FundDetails("MA0000030520", true, true, new BigDecimal("743.5"), new BigDecimal("500"), "Wafa Oblig Plus"));
        when(subscriptionPlanRepository.nextReferenceNumber()).thenReturn(sequenceValue);
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
         //
        when(subscriptionPlanRepository.existsByClientSecAccountAndAssetCodeAndStatusNot(
                "0000000001", "MA0000030520", PlanStatus.CANCELLED)).thenReturn(false);
        SubscriptionPlan created = newService().createPlan(new CreatePlanRequest(
                "0000000001", "1234567890", "MA0000030520", true,
                new BigDecimal("500"), null, PlanFrequency.MONTHLY, 5
        ));
        assertThat(created.getReference()).isEqualTo(expectedReference);
        verify(subscriptionPlanRepository).nextReferenceNumber();
        verify(subscriptionPlanRepository, never()).count();
        verify(subscriptionPlanRepository, never()).existsByReference(anyString());
        verify(auditLogService).log("PLAN_CREATED", "PLAN", expectedReference, "SUCCESS", created, null);
    }

    @Test
    void shouldRejectSecondPlanForSameSecAccountAndOpcvm() {
        when(accountsCatalogPort.findRelationship("0000000001", "1234567890"))
                .thenReturn(new AccountRelationship("0000000001", "1234567890", true));
        when(opcvmCatalogPort.getFund("MA0000030520"))
                .thenReturn(new FundDetails("MA0000030520", true, true, new BigDecimal("743.5"), new BigDecimal("500"), "Wafa Oblig Plus"));
        when(subscriptionPlanRepository.existsByClientSecAccountAndAssetCodeAndStatusNot(
                "0000000001", "MA0000030520", PlanStatus.CANCELLED)).thenReturn(true);
        assertThatThrownBy(() -> newService().createPlan(new CreatePlanRequest(
                "0000000001", "1234567890", "MA0000030520", true,
                new BigDecimal("500"), null, PlanFrequency.MONTHLY, 5
        )))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(subscriptionPlanRepository, never()).save(any());
    }

    @Test
    void shouldSuspendActivePlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setStatus(PlanStatus.ACTIVE);
        when(subscriptionPlanRepository.findByReference("EP000001")).thenReturn(Optional.of(plan));
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        PlanManagementService service = newService();
        SubscriptionPlan suspended = service.suspendPlan("EP000001");
        assertThat(suspended.getStatus()).isEqualTo(PlanStatus.SUSPENDED);
    }
    @Test
    void shouldRejectSuspendWhenPlanIsCancelled() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setStatus(PlanStatus.CANCELLED);
        when(subscriptionPlanRepository.findByReference("EP000001")).thenReturn(Optional.of(plan));
        PlanManagementService service = newService();
        assertThatThrownBy(() -> service.suspendPlan("EP000001"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }
    @Test
    void shouldRejectCancelWhenPlanIsAlreadyCancelled() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setStatus(PlanStatus.CANCELLED);
        when(subscriptionPlanRepository.findByReference("EP000001")).thenReturn(Optional.of(plan));
        PlanManagementService service = newService();
        assertThatThrownBy(() -> service.cancelPlan("EP000001"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
        verify(subscriptionPlanRepository, never()).save(any());
    }
    @Test
    void shouldResumeSuspendedPlanAsActive() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setClientSecAccount("0000000001");
        plan.setClientCashAccount("1234567890");
        plan.setAssetCode("MA0000000001");
        plan.setByAmount(true);
        plan.setStatus(PlanStatus.SUSPENDED);
        plan.setFrequency(PlanFrequency.MONTHLY);
        plan.setPreferredDayOfMonth(5);
        when(subscriptionPlanRepository.findByReference("EP000001")).thenReturn(Optional.of(plan));
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        SubscriptionPlan resumed = newService().resumePlan("EP000001");
        PlanSchedulePolicy policy = new PlanSchedulePolicy();
        LocalDate nominal = policy.calculateInitialNominalCycleDate(TODAY, PlanFrequency.MONTHLY, 5);
        LocalDate cycle = policy.firstExecutableDate(nominal);
        assertThat(resumed.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(resumed.getCurrentNominalCycleDate()).isEqualTo(nominal);
        assertThat(resumed.getCurrentCycleDueDate()).isEqualTo(cycle);
        assertThat(resumed.getNextExecutionDate()).isEqualTo(cycle);
        assertThat(resumed.getRetryDeadlineDate()).isEqualTo(policy.calculateRetryDeadline(cycle));
    }

    @Test
    void shouldRejectPlanWhenRecurringAmountIsBelowFundMinimum() {
        when(accountsCatalogPort.findRelationship("0000000001", "1234567890"))
                .thenReturn(new AccountRelationship("0000000001", "1234567890", true));
        when(opcvmCatalogPort.getFund("MA0000030520"))
                .thenReturn(new FundDetails("MA0000030520", true, true, new BigDecimal("743.5"), new BigDecimal("500"), "Wafa Oblig Plus"));
        PlanManagementService service = newService();
        assertThatThrownBy(() -> service.createPlan(new CreatePlanRequest(
                "0000000001",
                "1234567890",
                "MA0000030520",
                true,
                new BigDecimal("30"),
                null,
                PlanFrequency.MONTHLY,
                5
        )))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }


    @Test
    void shouldAuditFieldChangesOnUpdate() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setClientSecAccount("0000000001");
        plan.setClientCashAccount("1234567890");
        plan.setAssetCode("MA0000030520");
        plan.setByAmount(true);
        plan.setAmount(new BigDecimal("100"));
        plan.setFrequency(PlanFrequency.MONTHLY);
        plan.setPreferredDayOfMonth(5);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setCurrentCycleDueDate(LocalDate.of(2026, 5, 5));
        plan.setNextExecutionDate(LocalDate.of(2026, 5, 5));
        plan.setRetryDeadlineDate(LocalDate.of(2026, 5, 12));
        when(subscriptionPlanRepository.findByReference("EP000001")).thenReturn(Optional.of(plan));
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        when(opcvmCatalogPort.getFund("MA0000030520"))
                .thenReturn(new FundDetails("MA0000030520", true, true, new BigDecimal("743.5"), new BigDecimal("50"), "Wafa Oblig Plus"));
        newService().updatePlan("EP000001", new UpdatePlanRequest(
                null, null, null, new BigDecimal("200"), null, null, 10
        ));
        ArgumentCaptor<PlanUpdatedAudit> payload = ArgumentCaptor.forClass(PlanUpdatedAudit.class);
        verify(auditLogService).log(
                eq("PLAN_UPDATED"), eq("PLAN"), eq("EP000001"), eq("SUCCESS"), payload.capture(), isNull());
        assertThat(payload.getValue().changes()).containsExactly(
                new PlanFieldChange("amount", "100", "200"),
                new PlanFieldChange("preferredDayOfMonth", "5", "10")
        );
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSwitchModeAndClearPreviousValueWithoutRescheduling(boolean byAmount) {
        SubscriptionPlan plan = updateablePlan();
        plan.setByAmount(!byAmount);
        plan.setAmount(byAmount ? null : new BigDecimal("500"));
        plan.setQuantity(byAmount ? BigDecimal.TEN : null);
        stubUpdate(plan);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        SubscriptionPlan updated = newService().updatePlan(plan.getReference(), new UpdatePlanRequest(
                null, null, byAmount, byAmount ? new BigDecimal("600") : null,
                byAmount ? null : BigDecimal.ONE, null, null));
        assertThat(updated.isByAmount()).isEqualTo(byAmount);
        assertThat(updated.getAmount()).isEqualTo(byAmount ? new BigDecimal("600") : null);
        assertThat(updated.getQuantity()).isEqualTo(byAmount ? null : BigDecimal.ONE);
        assertCycleDates(updated, TODAY.plusMonths(1), TODAY.plusMonths(1), TODAY.plusMonths(1).plusDays(7));
        verify(auditLogService).log("PLAN_UPDATED", "PLAN", plan.getReference(), "SUCCESS", updated, null);
    }
    @ParameterizedTest
    @CsvSource({
            "true, , ",
            "false, , ",
            "true, 0, ",
            "false, , -1",
            "true, 499, ",
            "true, 600, 1",
            "false, 600, 1"
    })
    void shouldRejectInvalidModeChangeWithoutMutatingPlan(boolean byAmount, BigDecimal amount, BigDecimal quantity) {
        SubscriptionPlan plan = updateablePlan();
        plan.setByAmount(!byAmount);
        BigDecimal originalAmount = byAmount ? null : new BigDecimal("500");
        BigDecimal originalQuantity = byAmount ? BigDecimal.TEN : null;
        plan.setAmount(originalAmount);
        plan.setQuantity(originalQuantity);
        stubUpdate(plan);
        assertThatThrownBy(() -> newService().updatePlan(plan.getReference(), new UpdatePlanRequest(
                null, null, byAmount, amount, quantity, null, null)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(plan.isByAmount()).isEqualTo(!byAmount);
        assertThat(plan.getAmount()).isEqualTo(originalAmount);
        assertThat(plan.getQuantity()).isEqualTo(originalQuantity);
        verify(subscriptionPlanRepository, never()).save(any());
    }
    @ParameterizedTest
    @MethodSource("nonSchedulingUpdates")
    void shouldPreserveFutureAndRetryCyclesForNonSchedulingUpdates(UpdatePlanRequest request) {
        SubscriptionPlan plan = updateablePlan();
        stubUpdate(plan);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        if (request.clientCashAccount() != null) {
            when(accountsCatalogPort.findRelationship(plan.getClientSecAccount(), request.clientCashAccount()))
                    .thenReturn(new AccountRelationship(plan.getClientSecAccount(), request.clientCashAccount(), true));
        }
        newService().updatePlan(plan.getReference(), request);
        assertCycleDates(plan, TODAY.plusMonths(1), TODAY.plusMonths(1), TODAY.plusMonths(1).plusDays(7));
        plan.setCurrentNominalCycleDate(TODAY.minusDays(2));
        plan.setCurrentCycleDueDate(TODAY.minusDays(2));
        plan.setNextExecutionDate(TODAY.plusDays(1));
        plan.setRetryDeadlineDate(TODAY.plusDays(5));
        plan.setLastAttemptDate(TODAY);
        newService().updatePlan(plan.getReference(), request);
        assertCycleDates(plan, TODAY.minusDays(2), TODAY.plusDays(1), TODAY.plusDays(5));
        assertThat(plan.getLastAttemptDate()).isEqualTo(TODAY);
        if (request.recurringAmount() != null) {
            assertThat(plan.getAmount()).isEqualTo(request.recurringAmount());
        }
        if (request.clientCashAccount() != null) {
            assertThat(plan.getClientCashAccount()).isEqualTo(request.clientCashAccount());
        }
    }
    static Stream<UpdatePlanRequest> nonSchedulingUpdates() {
        return Stream.of(
                new UpdatePlanRequest(null, null, null, null, null, null, null),
                new UpdatePlanRequest("1234567891", null, null, null, null, null, null),
                new UpdatePlanRequest(null, null, null, new BigDecimal("600"), null, null, null),
                new UpdatePlanRequest(null, null, true, null, null, PlanFrequency.MONTHLY, 5),
                new UpdatePlanRequest(null, "MA0000030520", null, null, null, null, null));
    }
    @ParameterizedTest
    @CsvSource({"QUARTERLY, , 2026-11-05, 2026-11-12", ", 20, 2026-08-20, 2026-08-27",
            "QUARTERLY, 5, 2026-11-05, 2026-11-12"})
    void shouldRescheduleFutureCycleOnlyForChangedSchedule(PlanFrequency frequency, Integer day,
                                                           LocalDate expectedDue, LocalDate expectedDeadline) {
        SubscriptionPlan plan = updateablePlan();
        stubUpdate(plan);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        newService().updatePlan(plan.getReference(), new UpdatePlanRequest(null, null, null, null, null, frequency, day));
        assertCycleDates(plan, expectedDue, expectedDue, expectedDeadline);
        assertThat(plan.getFrequency()).isEqualTo(frequency == null ? PlanFrequency.MONTHLY : frequency);
        assertThat(plan.getPreferredDayOfMonth()).isEqualTo(day == null ? 5 : day);
    }
    @ParameterizedTest
    @CsvSource({"ACTIVE, 0", "ACTIVE, -2", "ACTIVE, -30", "PAUSED, -2", "SUSPENDED, -2"})
    void shouldDeferScheduleChangeForDueCycleWithoutResettingRetries(PlanStatus status, int dueOffset) {
        SubscriptionPlan plan = updateablePlan();
        plan.setStatus(status);
        LocalDate dueDate = TODAY.plusDays(dueOffset);
        LocalDate deadline = new PlanSchedulePolicy().calculateRetryDeadline(dueDate);
        plan.setCurrentNominalCycleDate(dueDate);
        plan.setCurrentCycleDueDate(dueDate);
        plan.setNextExecutionDate(TODAY.plusDays(1));
        plan.setRetryDeadlineDate(deadline);
        plan.setLastAttemptDate(TODAY);
        stubUpdate(plan);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        newService().updatePlan(plan.getReference(), new UpdatePlanRequest(
                null, null, null, null, null, PlanFrequency.QUARTERLY, 20));
        assertThat(plan.getFrequency()).isEqualTo(PlanFrequency.QUARTERLY);
        assertThat(plan.getPreferredDayOfMonth()).isEqualTo(20);
        assertCycleDates(plan, dueDate, TODAY.plusDays(1), deadline);
        assertThat(plan.getLastAttemptDate()).isEqualTo(TODAY);
        assertThat(plan.getStatus()).isEqualTo(status);
    }
    @Test
    void shouldPreserveNominalMonthWhenPatchingRolledOverDueCycle() {
        SubscriptionPlan plan = updateablePlan();
        plan.setCurrentNominalCycleDate(LocalDate.of(2026, 1, 31));
        plan.setCurrentCycleDueDate(LocalDate.of(2026, 2, 2));
        plan.setNextExecutionDate(LocalDate.of(2026, 2, 3));
        plan.setRetryDeadlineDate(LocalDate.of(2026, 2, 9));
        stubUpdate(plan);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        newService().updatePlan(plan.getReference(), new UpdatePlanRequest(
                null, null, null, null, null, PlanFrequency.QUARTERLY, 20));
        assertThat(plan.getCurrentNominalCycleDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertCycleDates(plan, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 9));
    }
    @Test
    void shouldPersistNominalDateWhenFutureScheduleRollsIntoNextMonth() {
        SubscriptionPlan plan = updateablePlan();
        stubUpdate(plan);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        LocalDate today = LocalDate.of(2026, 1, 1);
        newService(Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .updatePlan(plan.getReference(), new UpdatePlanRequest(null, null, null, null, null, null, 31));
        assertThat(plan.getCurrentNominalCycleDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertCycleDates(plan, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 9));
    }
    @ParameterizedTest
    @ValueSource(strings = {"PAUSED", "SUSPENDED"})
    void shouldAnchorResumedCycleBeforeWeekendAdjustment(PlanStatus status) {
        SubscriptionPlan plan = updateablePlan();
        plan.setStatus(status);
        plan.setPreferredDayOfMonth(31);
        when(subscriptionPlanRepository.findByReference(plan.getReference())).thenReturn(Optional.of(plan));
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        LocalDate today = LocalDate.of(2026, 1, 31);
        newService(Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))
                .resumePlan(plan.getReference());
        assertThat(plan.getCurrentNominalCycleDate()).isEqualTo(today);
        assertCycleDates(plan, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 9));
        assertThat(new PlanSchedulePolicy().calculateNextNominalCycleDate(
                plan.getCurrentNominalCycleDate(), plan.getFrequency(), plan.getPreferredDayOfMonth()))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }
    private SubscriptionPlan updateablePlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setClientSecAccount("0000000001");
        plan.setClientCashAccount("1234567890");
        plan.setAssetCode("MA0000030520");
        plan.setByAmount(true);
        plan.setAmount(new BigDecimal("500"));
        plan.setFrequency(PlanFrequency.MONTHLY);
        plan.setPreferredDayOfMonth(5);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setCurrentNominalCycleDate(TODAY.plusMonths(1));
        plan.setCurrentCycleDueDate(TODAY.plusMonths(1));
        plan.setNextExecutionDate(TODAY.plusMonths(1));
        plan.setRetryDeadlineDate(TODAY.plusMonths(1).plusDays(7));
        return plan;
    }
    private void stubUpdate(SubscriptionPlan plan) {
        when(subscriptionPlanRepository.findByReference(plan.getReference())).thenReturn(Optional.of(plan));
        when(opcvmCatalogPort.getFund(plan.getAssetCode()))
                .thenReturn(new FundDetails(plan.getAssetCode(), true, true, BigDecimal.TEN, new BigDecimal("500"), null));
    }
    private void assertCycleDates(SubscriptionPlan plan, LocalDate due, LocalDate next, LocalDate deadline) {
        assertThat(plan.getCurrentCycleDueDate()).isEqualTo(due);
        assertThat(plan.getNextExecutionDate()).isEqualTo(next);
        assertThat(plan.getRetryDeadlineDate()).isEqualTo(deadline);
    }
    private PlanManagementService newService() {
        return newService(FIXED_CLOCK);
    }
    private PlanManagementService newService(Clock clock) {
        return new PlanManagementService(
                subscriptionPlanRepository,
                accountsCatalogPort,
                opcvmCatalogPort,
                new PlanFactory(new PlanSchedulePolicy()),
                new PlanSchedulePolicy(),
                auditLogService,
                clock
        );
    }
}