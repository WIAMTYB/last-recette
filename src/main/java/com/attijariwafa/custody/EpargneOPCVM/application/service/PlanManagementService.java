package com.attijariwafa.custody.EpargneOPCVM.application.service;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PlanManagementService {
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AccountsCatalogPort accountsCatalogPort;
    private final OpcvmCatalogPort opcvmCatalogPort;
    private final PlanFactory planFactory;
    private final PlanSchedulePolicy schedulePolicy;
    private final AuditLogService auditLogService;
    private final Clock clock;
    public PlanManagementService(SubscriptionPlanRepository subscriptionPlanRepository,
                                 AccountsCatalogPort accountsCatalogPort,
                                 OpcvmCatalogPort opcvmCatalogPort,
                                 PlanFactory planFactory,
                                 PlanSchedulePolicy schedulePolicy,
                                 AuditLogService auditLogService,
                                 Clock clock) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.accountsCatalogPort = accountsCatalogPort;
        this.opcvmCatalogPort = opcvmCatalogPort;
        this.planFactory = planFactory;
        this.schedulePolicy = schedulePolicy;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }
    @Transactional
    public SubscriptionPlan createPlan(CreatePlanRequest request) {
        validateRelationship(request.clientSecAccount(), request.clientCashAccount());
        FundDetails fund = validateFund(request.assetCode());
        validateAmounts(request.byAmount(), request.recurringAmount(), request.recurringQuantity(), fund);
        if (subscriptionPlanRepository.existsByClientSecAccountAndAssetCodeAndStatusNot(
                request.clientSecAccount(),
                request.assetCode(),
                PlanStatus.CANCELLED)) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_ERROR,
                    "An active plan already exists for this securities account and OPCVM"
            );
        }
        SubscriptionPlan plan = planFactory.create(request, nextReference(), LocalDate.now(clock));
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.log("PLAN_CREATED", "PLAN", saved.getReference(), "SUCCESS", saved, null);
        return saved;
    }
    @Transactional
    public SubscriptionPlan updatePlan(String reference, UpdatePlanRequest request) {
        SubscriptionPlan plan = load(reference);
        if (plan.getStatus() == PlanStatus.CANCELLED) {
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION, "Cancelled plans cannot be updated");
        }
        if (request.assetCode() != null && !request.assetCode().equals(plan.getAssetCode())) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Asset change is not allowed on an existing plan. Create a new plan instead."
            );
        }
        String oldCash = plan.getClientCashAccount();
        boolean oldByAmount = plan.isByAmount();
        BigDecimal oldAmount = plan.getAmount();
        BigDecimal oldQuantity = plan.getQuantity();
        PlanFrequency oldFrequency = plan.getFrequency();
        int oldDay = plan.getPreferredDayOfMonth();
        boolean byAmount = request.byAmount() != null ? request.byAmount() : plan.isByAmount();
        boolean modeChanged = byAmount != plan.isByAmount();
        BigDecimal amount = request.recurringAmount() != null
                ? request.recurringAmount() : (modeChanged ? null : plan.getAmount());
        BigDecimal quantity = request.recurringQuantity() != null
                ? request.recurringQuantity() : (modeChanged ? null : plan.getQuantity());
        validateAmounts(byAmount, amount, quantity, validateFund(plan.getAssetCode()));
        boolean scheduleChanged = (request.frequency() != null && request.frequency() != plan.getFrequency())
                || (request.preferredDayOfMonth() != null
                && request.preferredDayOfMonth() != plan.getPreferredDayOfMonth());
        if (request.clientCashAccount() != null && !request.clientCashAccount().equals(plan.getClientCashAccount())) {
            validateRelationship(plan.getClientSecAccount(), request.clientCashAccount());
            plan.setClientCashAccount(request.clientCashAccount());
        }
        plan.setByAmount(byAmount);
        plan.setAmount(amount);
        plan.setQuantity(quantity);
        if (request.frequency() != null) {
            plan.setFrequency(request.frequency());
        }
        if (request.preferredDayOfMonth() != null) {
            plan.setPreferredDayOfMonth(request.preferredDayOfMonth());
        }
        LocalDate today = LocalDate.now(clock);
        // Once due, preserve the cycle identity and retry window. The execution service
        // applies the updated schedule when advancing after success or a skipped cycle.
        if (scheduleChanged && plan.getCurrentCycleDueDate().isAfter(today)) {
            LocalDate nominalCycle = schedulePolicy.calculateInitialNominalCycleDate(
                    today,
                    plan.getFrequency(),
                    plan.getPreferredDayOfMonth()
            );
            LocalDate recalculatedCycle = schedulePolicy.firstExecutableDate(nominalCycle);
            plan.setCurrentNominalCycleDate(nominalCycle);
            plan.setCurrentCycleDueDate(recalculatedCycle);
            plan.setNextExecutionDate(recalculatedCycle);
            plan.setRetryDeadlineDate(schedulePolicy.calculateRetryDeadline(recalculatedCycle));
        }
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.log(
                "PLAN_UPDATED",
                "PLAN",
                saved.getReference(),
                "SUCCESS",
                new PlanUpdatedAudit(detectChanges(oldCash, oldByAmount, oldAmount, oldQuantity, oldFrequency, oldDay, saved)),
                null
        );
        return saved;
    }
    @Transactional
    public SubscriptionPlan pausePlan(String reference) {
        SubscriptionPlan plan = load(reference);
        if (plan.getStatus() != PlanStatus.ACTIVE && plan.getStatus() != PlanStatus.SUSPENDED) {
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION, "Only active or suspended plans can be paused");
        }
        plan.setStatus(PlanStatus.PAUSED);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.log("PLAN_PAUSED", "PLAN", saved.getReference(), "SUCCESS", saved, null);
        return saved;
    }
    @Transactional
    public SubscriptionPlan suspendPlan(String reference) {
        SubscriptionPlan plan = load(reference);
        if (plan.getStatus() != PlanStatus.ACTIVE && plan.getStatus() != PlanStatus.PAUSED) {
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION, "Only active or paused plans can be suspended");
        }
        plan.setStatus(PlanStatus.SUSPENDED);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.log("PLAN_SUSPENDED", "PLAN", saved.getReference(), "SUCCESS", saved, null);
        return saved;
    }
    @Transactional
    public SubscriptionPlan resumePlan(String reference) {
        SubscriptionPlan plan = load(reference);
        if (plan.getStatus() != PlanStatus.PAUSED && plan.getStatus() != PlanStatus.SUSPENDED) {
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION, "Only paused or suspended plans can be resumed");
        }
        plan.setStatus(PlanStatus.ACTIVE);
        LocalDate today = LocalDate.now(clock);
        LocalDate nominal = schedulePolicy.calculateInitialNominalCycleDate(
                today, plan.getFrequency(), plan.getPreferredDayOfMonth());
        LocalDate cycleDate = schedulePolicy.firstExecutableDate(nominal);
        plan.setCurrentNominalCycleDate(nominal);
        plan.setCurrentCycleDueDate(cycleDate);
        plan.setNextExecutionDate(cycleDate);
        plan.setRetryDeadlineDate(schedulePolicy.calculateRetryDeadline(cycleDate));
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.log("PLAN_RESUMED", "PLAN", saved.getReference(), "SUCCESS", saved, null);
        return saved;
    }
    @Transactional
    public SubscriptionPlan cancelPlan(String reference) {
        SubscriptionPlan plan = load(reference);
        if (plan.getStatus() == PlanStatus.CANCELLED) {
            throw new ApplicationException(ErrorCode.INVALID_STATUS_TRANSITION, "Plan is already cancelled");
        }
        plan.setStatus(PlanStatus.CANCELLED);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        auditLogService.log("PLAN_CANCELLED", "PLAN", saved.getReference(), "SUCCESS", saved, null);
        return saved;
    }
    public SubscriptionPlan load(String reference) {
        return subscriptionPlanRepository.findByReference(reference)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + reference));
    }
    private void validateRelationship(String secAccount, String cashAccount) {
        AccountRelationship relationship = accountsCatalogPort.findRelationship(secAccount, cashAccount);
        if (!relationship.valid()) {
            throw new ApplicationException(ErrorCode.ACCOUNT_RELATIONSHIP_NOT_FOUND, "Security account is not linked to the cash account");
        }
    }
    private FundDetails validateFund(String assetCode) {
        FundDetails fund = opcvmCatalogPort.getFund(assetCode);
        if (!fund.active()) {
            throw new ApplicationException(ErrorCode.ASSET_NOT_FOUND, "Fund is not active: " + assetCode);
        }
        if (!fund.acceptedForScheduledSavings()) {
            throw new ApplicationException(
                    ErrorCode.ASSET_NOT_FOUND,
                    "Fund is not eligible for scheduled savings: " + assetCode
            );
        }
        return fund;
    }
    private void validateAmounts(boolean byAmount, BigDecimal amount, BigDecimal quantity, FundDetails fund) {
        if (byAmount) {
            if (amount == null || amount.signum() <= 0 || quantity != null) {
                throw new ApplicationException(ErrorCode.VALIDATION_ERROR, "Amount-based plans require a positive amount and no quantity");
            }
            validateMinimumRecurringAmount(amount, fund);
            return;
        }
        if (quantity == null || quantity.signum() <= 0 || amount != null) {
            throw new ApplicationException(ErrorCode.VALIDATION_ERROR, "Quantity-based plans require a positive quantity and no amount");
        }
    }
    private void validateMinimumRecurringAmount(BigDecimal amount, FundDetails fund) {
        BigDecimal minimumAmount = fund.minimumRecurringAmount();
        if (minimumAmount == null || minimumAmount.signum() <= 0) {
            return;
        }
        if (amount.compareTo(minimumAmount) < 0) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Recurring amount must be at least " + minimumAmount
            );
        }
    }
    private static List<PlanFieldChange> detectChanges(String oldCash,
                                                       boolean oldByAmount,
                                                       BigDecimal oldAmount,
                                                       BigDecimal oldQuantity,
                                                       PlanFrequency oldFrequency,
                                                       int oldDay,
                                                       SubscriptionPlan saved) {
        List<PlanFieldChange> changes = new ArrayList<>();
        addChange(changes, "clientCashAccount", oldCash, saved.getClientCashAccount());
        addChange(changes, "byAmount", String.valueOf(oldByAmount), String.valueOf(saved.isByAmount()));
        addDecimalChange(changes, "amount", oldAmount, saved.getAmount());
        addDecimalChange(changes, "quantity", oldQuantity, saved.getQuantity());
        addChange(changes, "frequency", stringValue(oldFrequency), stringValue(saved.getFrequency()));
        addChange(changes, "preferredDayOfMonth", String.valueOf(oldDay), String.valueOf(saved.getPreferredDayOfMonth()));
        return changes;
    }
    private static void addDecimalChange(List<PlanFieldChange> changes, String field, BigDecimal from, BigDecimal to) {
        if (from == null && to == null) {
            return;
        }
        if (from != null && to != null && from.compareTo(to) == 0) {
            return;
        }
        addChange(changes, field, formatDecimal(from), formatDecimal(to));
    }
    private static void addChange(List<PlanFieldChange> changes, String field, String from, String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        changes.add(new PlanFieldChange(field, from, to));
    }
    private static String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
    private String nextReference() {
        return String.format(Locale.ROOT, "EP%06d", subscriptionPlanRepository.nextReferenceNumber());
    }
}