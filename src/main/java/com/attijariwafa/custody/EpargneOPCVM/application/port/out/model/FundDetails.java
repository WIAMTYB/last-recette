package com.attijariwafa.custody.EpargneOPCVM.application.port.out.model;

import java.math.BigDecimal;

public record FundDetails(
        String assetCode,
        boolean active,
        boolean acceptedForScheduledSavings,
        BigDecimal latestNav,
        BigDecimal minimumRecurringAmount,
        String name
) {
}
