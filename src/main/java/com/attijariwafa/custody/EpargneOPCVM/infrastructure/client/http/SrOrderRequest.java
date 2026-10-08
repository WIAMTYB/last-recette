package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SrOrderRequest(
        @JsonProperty("clientReference") String clientReference,
        @JsonProperty("clientSecAccount") String clientSecAccount,
        @JsonProperty("clientCashAccount") String clientCashAccount,
        @JsonProperty("sRType") String sRType,
        @JsonProperty("tradableAsset") String tradableAsset,
        @JsonProperty("byAmount") String byAmount,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("quantity") BigDecimal quantity,
        @JsonProperty("source") String source
) {
}
