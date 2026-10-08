package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CashBalanceResponse(
        @JsonProperty("returnCode") int returnCode,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("soldeDisponible") BigDecimal soldeDisponible
) {
}
