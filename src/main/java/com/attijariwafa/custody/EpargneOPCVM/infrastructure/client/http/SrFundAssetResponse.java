package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SrFundAssetResponse(
        String isin,
        Boolean active,
        Boolean acceptedForScheduledSavings,
        BigDecimal minimumRecurringAmount,
        BigDecimal netAssetValue,
        String description
) {
}
