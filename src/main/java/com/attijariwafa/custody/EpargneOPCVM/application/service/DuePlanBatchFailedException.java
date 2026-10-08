package com.attijariwafa.custody.EpargneOPCVM.application.service;

/**
 * Raised when the batch loop itself breaks (as opposed to a single plan failing, which
 * is recorded as an execution attempt). Carries the plans processed before the break so
 * the run record keeps their counters.
 */
public class DuePlanBatchFailedException extends RuntimeException {

    private final transient DuePlanBatchResult partialResult;

    public DuePlanBatchFailedException(DuePlanBatchResult partialResult, RuntimeException cause) {
        super("Due-plan batch failed after " + partialResult.processedCount() + " plans: " + cause.getMessage(), cause);
        this.partialResult = partialResult;
    }

    public DuePlanBatchResult partialResult() {
        return partialResult;
    }

    @Override
    public synchronized RuntimeException getCause() {
        return (RuntimeException) super.getCause();
    }
}
