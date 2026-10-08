package com.attijariwafa.custody.api_token_admin;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

final class SecureTokenGenerator {
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom;

    SecureTokenGenerator() {
        this(new SecureRandom());
    }

    SecureTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    GeneratedToken generate(String tokenIdPrefix, int tokenIdBytes, int secretBytes, int bcryptStrength) {
        if (tokenIdBytes < 16) {
            throw new IllegalArgumentException("token-id-bytes must be at least 16");
        }
        if (secretBytes < 32) {
            throw new IllegalArgumentException("secret-bytes must be at least 32");
        }
        if (bcryptStrength < 10 || bcryptStrength > 16) {
            throw new IllegalArgumentException("bcrypt-strength must be between 10 and 16");
        }

        String tokenId = tokenIdPrefix + randomBase64Url(tokenIdBytes);
        String secret = randomBase64Url(secretBytes);
        String secretHash = new BCryptPasswordEncoder(bcryptStrength, secureRandom).encode(secret);
        return new GeneratedToken(tokenId, secret, secretHash);
    }

    private String randomBase64Url(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}
