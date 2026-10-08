package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

public enum PlanFrequency {
    MONTHLY(1),
    QUARTERLY(3);

    private final int months;

    PlanFrequency(int months) {
        this.months = months;
    }

    public int months() {
        return months;
    }
}
