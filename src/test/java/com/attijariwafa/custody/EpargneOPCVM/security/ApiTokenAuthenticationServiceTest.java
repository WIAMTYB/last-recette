package com.attijariwafa.custody.EpargneOPCVM.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class ApiTokenAuthenticationServiceTest {

    private static final Instant START = Instant.parse("2026-09-16T10:00:00Z");

    private final ApiTokenRepository repository = mock(ApiTokenRepository.class);
    private final AtomicInteger slowHashCalls = new AtomicInteger();
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence rawPassword) {
            return "hash:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            slowHashCalls.incrementAndGet();
            return encodedPassword.equals("hash:" + rawPassword);
        }
    };
    private final ApiTokenProperties properties = new ApiTokenProperties();
    private MutableClock clock;
    private ApiTokenAuthenticationService service;

    @BeforeEach
    void setUp() {
        properties.setCacheTtl(Duration.ofSeconds(60));
        clock = new MutableClock(START);
        service = new ApiTokenAuthenticationService(repository, encoder, properties, clock);
        when(repository.findByTokenId("cargo")).thenReturn(Optional.of(new ApiTokenRecord(
                "cargo", "CARGO", "hash:s3cret", true, null,
                List.of(new ApiTokenAuthorization("/api/v1/plans/**", null)))));
    }

    @Test
    void shouldRunTheSlowHashOnceWhileTheVerifiedSecretIsCached() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET").status())
                    .isEqualTo(ApiTokenAuthenticationService.Status.AUTHENTICATED);
        }

        assertThat(slowHashCalls).hasValue(1);
        verify(repository, times(1)).findByTokenId(anyString());
    }

    @Test
    void shouldReHashAfterTheCacheTtlElapses() {
        service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET");
        clock.advance(Duration.ofSeconds(61));
        service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET");

        assertThat(slowHashCalls).hasValue(2);
    }

    @Test
    void shouldNeverCacheAWrongSecret() {
        for (int i = 0; i < 3; i++) {
            assertThat(service.authenticate("cargo.wrong", "/api/v1/plans/stats", "GET").status())
                    .isEqualTo(ApiTokenAuthenticationService.Status.UNAUTHORIZED);
        }

        assertThat(slowHashCalls).hasValue(3);
    }

    @Test
    void shouldNotReuseACachedSecretForAnotherToken() {
        when(repository.findByTokenId("other")).thenReturn(Optional.of(new ApiTokenRecord(
                "other", "OTHER", "hash:different", true, null,
                List.of(new ApiTokenAuthorization("/api/v1/plans/**", null)))));

        service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET");
        assertThat(service.authenticate("other.s3cret", "/api/v1/plans/stats", "GET").status())
                .isEqualTo(ApiTokenAuthenticationService.Status.UNAUTHORIZED);

        assertThat(slowHashCalls).hasValue(2);
    }

    @Test
    void shouldStillEnforceAuthorizationsOnCacheHits() {
        service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET");

        assertThat(service.authenticate("cargo.s3cret", "/actuator/prometheus", "GET").status())
                .isEqualTo(ApiTokenAuthenticationService.Status.FORBIDDEN);
        assertThat(slowHashCalls).hasValue(1);
    }

    @Test
    void shouldHashEveryRequestWhenTheCacheIsDisabled() {
        properties.setCacheTtl(Duration.ZERO);

        service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET");
        service.authenticate("cargo.s3cret", "/api/v1/plans/stats", "GET");

        assertThat(slowHashCalls).hasValue(2);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
