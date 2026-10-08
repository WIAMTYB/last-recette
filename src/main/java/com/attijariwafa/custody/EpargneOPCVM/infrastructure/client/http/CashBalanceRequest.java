package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CashBalanceRequest(
        @JsonProperty("caller") String caller,
        @JsonProperty("compte") String compte,
        @JsonProperty("correlationId") String correlationId
) {
}
