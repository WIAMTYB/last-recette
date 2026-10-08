package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderAckResponse(
        @JsonProperty("returnCode") int returnCode,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("mainReference") String mainReference
) {
}
