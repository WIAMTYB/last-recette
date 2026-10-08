package com.attijariwafa.custody.EpargneOPCVM.security;

import java.time.LocalDateTime;
import java.util.List;

public record ApiTokenRecord(
        String tokenId,
        String clientCode,
        String secretHash,
        boolean active,
        LocalDateTime expiresAt,
        List<ApiTokenAuthorization> authorizations) {

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
