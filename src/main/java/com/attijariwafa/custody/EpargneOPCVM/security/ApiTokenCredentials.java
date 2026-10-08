package com.attijariwafa.custody.EpargneOPCVM.security;

import java.util.Optional;

public record ApiTokenCredentials(String tokenId, String secret) {
    public static Optional<ApiTokenCredentials> parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        String trimmed = headerValue.trim();
        int separator = trimmed.indexOf('.');
        if (separator <= 0 || separator == trimmed.length() - 1 || trimmed.indexOf('.', separator + 1) != -1) {
            return Optional.empty();
        }
        return Optional.of(new ApiTokenCredentials(
                trimmed.substring(0, separator),
                trimmed.substring(separator + 1)));
    }
}

