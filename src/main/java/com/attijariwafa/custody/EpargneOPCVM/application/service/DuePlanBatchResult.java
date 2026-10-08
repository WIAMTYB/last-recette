package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Accumulates what happened to every plan claimed by one execution batch so the run
 * record can be written without re-reading the execution table.
 */
public final class DuePlanBatchResult {

    public enum Disposition {
        /** An order was booked (or the cycle was already booked and the plan advanced). */
        EXECUTED,
        /** The attempt failed and the plan will be retried on the next business day. */
        RETRY_SCHEDULED,
        /** The attempt failed after the retry deadline; the cycle was skipped. */
        CYCLE_SKIPPED,
        /** No attempt: today is not a business day, the plan was pushed forward. */
        NON_BUSINESS_DAY_DEFERRED,
        /** No attempt: the plan was no longer active or due once locked. */
        NOT_DUE,
        CANCELLED
    }

    public record PlanOutcome(String reference, Disposition disposition, ExecutionResultStatus resultStatus) {
    }

    private final int batchSize;
    private final List<PlanOutcome> outcomes = new ArrayList<>();
    private int successCount;
    private int insufficientFundsCount;
    private int belowThresholdCount;
    private int orderRejectedCount;
    private int technicalFailureCount;
    private int retriesScheduledCount;
    private int cyclesSkippedCount;
    private int nonBusinessDayDeferredCount;

    public DuePlanBatchResult(int batchSize) {
        this.batchSize = batchSize;
    }

    void record(PlanOutcome outcome) {
        outcomes.add(outcome);
        if (outcome.resultStatus() != null) {
            switch (outcome.resultStatus()) {
                case SUCCESS -> successCount++;
                case INSUFFICIENT_FUNDS -> insufficientFundsCount++;
                case AMOUNT_BELOW_VL_THRESHOLD -> belowThresholdCount++;
                case ORDER_REJECTED -> orderRejectedCount++;
                case TECHNICAL_FAILURE -> technicalFailureCount++;
            }
        }
        switch (outcome.disposition()) {
            case RETRY_SCHEDULED -> retriesScheduledCount++;
            case CYCLE_SKIPPED -> cyclesSkippedCount++;
            case NON_BUSINESS_DAY_DEFERRED -> nonBusinessDayDeferredCount++;
            case EXECUTED, NOT_DUE -> {
            }
        }
    }

    public int batchSize() {
        return batchSize;
    }

    public List<PlanOutcome> outcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    public List<String> references() {
        return outcomes.stream().map(PlanOutcome::reference).toList();
    }

    public int processedCount() {
        return outcomes.size();
    }

    /** True when the batch stopped because it hit its size limit, so more plans may still be due. */
    public boolean batchExhausted() {
        return outcomes.size() >= batchSize;
    }

    public int successCount() {
        return successCount;
    }

    public int insufficientFundsCount() {
        return insufficientFundsCount;
    }

    public int belowThresholdCount() {
        return belowThresholdCount;
    }

    public int orderRejectedCount() {
        return orderRejectedCount;
    }

    public int technicalFailureCount() {
        return technicalFailureCount;
    }

    public int retriesScheduledCount() {
        return retriesScheduledCount;
    }

    public int cyclesSkippedCount() {
        return cyclesSkippedCount;
    }

    public int nonBusinessDayDeferredCount() {
        return nonBusinessDayDeferredCount;
    }
}
