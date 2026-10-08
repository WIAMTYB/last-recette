package com.attijariwafa.custody.api_token_admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureTokenGeneratorTests {

    @Test
    void generatesUniqueTokenMaterialAndVerifiableHash() {
        SecureTokenGenerator generator = new SecureTokenGenerator(new SecureRandom());

        GeneratedToken first = generator.generate("ak_", 16, 32, 10);
        GeneratedToken second = generator.generate("ak_", 16, 32, 10);

        assertTrue(first.tokenId().startsWith("ak_"));
        assertFalse(first.secret().isBlank());
        assertTrue(new BCryptPasswordEncoder().matches(first.secret(), first.secretHash()));
        assertNotEquals(first.tokenId(), second.tokenId());
        assertNotEquals(first.secret(), second.secret());
    }
}
