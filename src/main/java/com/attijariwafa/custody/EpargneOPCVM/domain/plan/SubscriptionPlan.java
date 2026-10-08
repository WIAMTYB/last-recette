package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "EPARGNE_SUBSCRIPTION_PLAN")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "OPT_LOCK_VERSION", nullable = false)
    private Long version;

    @Column(name = "REFERENCE", nullable = false, unique = true, length = 20)
    private String reference;

    @Column(name = "CLIENT_SEC_ACCOUNT", nullable = false, length = 20)
    private String clientSecAccount;

    @Column(name = "CLIENT_CASH_ACCOUNT", nullable = false, length = 30)
    private String clientCashAccount;

    @Column(name = "ASSET_CODE", nullable = false, length = 20)
    private String assetCode;

    @Column(name = "BY_AMOUNT", nullable = false)
    private boolean byAmount;

    @Column(name = "AMOUNT", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "QUANTITY", precision = 19, scale = 6)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "FREQUENCY", nullable = false, length = 20)
    private PlanFrequency frequency;

    @Column(name = "PREFERRED_DAY_OF_MONTH", nullable = false)
    private int preferredDayOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private PlanStatus status;

    @Column(name = "CURRENT_NOMINAL_CYCLE_DATE", nullable = false)
    private LocalDate currentNominalCycleDate;

    @Column(name = "CURRENT_CYCLE_DUE_DATE", nullable = false)
    private LocalDate currentCycleDueDate;

    @Column(name = "NEXT_EXECUTION_DATE", nullable = false)
    private LocalDate nextExecutionDate;

    @Column(name = "RETRY_DEADLINE_DATE", nullable = false)
    private LocalDate retryDeadlineDate;

    @Column(name = "LAST_ATTEMPT_DATE")
    private LocalDate lastAttemptDate;

    @Column(name = "LAST_EXECUTION_DATE")
    private LocalDate lastExecutionDate;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = PlanStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getClientSecAccount() {
        return clientSecAccount;
    }

    public void setClientSecAccount(String clientSecAccount) {
        this.clientSecAccount = clientSecAccount;
    }

    public String getClientCashAccount() {
        return clientCashAccount;
    }

    public void setClientCashAccount(String clientCashAccount) {
        this.clientCashAccount = clientCashAccount;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public boolean isByAmount() {
        return byAmount;
    }

    public void setByAmount(boolean byAmount) {
        this.byAmount = byAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public PlanFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(PlanFrequency frequency) {
        this.frequency = frequency;
    }

    public int getPreferredDayOfMonth() {
        return preferredDayOfMonth;
    }

    public void setPreferredDayOfMonth(int preferredDayOfMonth) {
        this.preferredDayOfMonth = preferredDayOfMonth;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public LocalDate getCurrentNominalCycleDate() {
        return currentNominalCycleDate;
    }

    public void setCurrentNominalCycleDate(LocalDate currentNominalCycleDate) {
        this.currentNominalCycleDate = currentNominalCycleDate;
    }

    public LocalDate getCurrentCycleDueDate() {
        return currentCycleDueDate;
    }

    public void setCurrentCycleDueDate(LocalDate currentCycleDueDate) {
        this.currentCycleDueDate = currentCycleDueDate;
    }

    public LocalDate getNextExecutionDate() {
        return nextExecutionDate;
    }

    public void setNextExecutionDate(LocalDate nextExecutionDate) {
        this.nextExecutionDate = nextExecutionDate;
    }

    public LocalDate getRetryDeadlineDate() {
        return retryDeadlineDate;
    }

    public void setRetryDeadlineDate(LocalDate retryDeadlineDate) {
        this.retryDeadlineDate = retryDeadlineDate;
    }

    public LocalDate getLastAttemptDate() {
        return lastAttemptDate;
    }

    public void setLastAttemptDate(LocalDate lastAttemptDate) {
        this.lastAttemptDate = lastAttemptDate;
    }

    public LocalDate getLastExecutionDate() {
        return lastExecutionDate;
    }

    public void setLastExecutionDate(LocalDate lastExecutionDate) {
        this.lastExecutionDate = lastExecutionDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
