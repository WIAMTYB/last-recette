package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

public enum ExecutionResultStatus {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    AMOUNT_BELOW_VL_THRESHOLD,
    ORDER_REJECTED,
    TECHNICAL_FAILURE
}
