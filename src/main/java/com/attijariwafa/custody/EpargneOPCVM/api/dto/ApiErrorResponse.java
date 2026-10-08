package com.attijariwafa.custody.EpargneOPCVM.api.dto;

public record ApiErrorResponse(
        int status,
        String code,
        String message
) {
}
