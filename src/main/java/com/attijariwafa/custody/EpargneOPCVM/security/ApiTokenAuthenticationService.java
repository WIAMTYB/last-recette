package com.attijariwafa.custody.EpargneOPCVM.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ApiTokenAuthenticationService {
    private static final int MAX_VERIFIED_SECRET_ENTRIES = 10_000;

    private final ApiTokenRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final ApiTokenProperties properties;
    private final Clock clock;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> verifiedSecrets = new ConcurrentHashMap<>();

    @Autowired
    public ApiTokenAuthenticationService(ApiTokenRepository repository,
                                         PasswordEncoder passwordEncoder,
                                         ApiTokenProperties properties) {
        this(repository, passwordEncoder, properties, Clock.systemDefaultZone());
    }

    ApiTokenAuthenticationService(ApiTokenRepository repository,
                                  PasswordEncoder passwordEncoder,
                                  ApiTokenProperties properties,
                                  Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.pathMatcher.setCaseSensitive(true);
        this.pathMatcher.setTrimTokens(false);
    }

    public AuthenticationResult authenticate(String headerValue, String requestPath, String requestMethod) {
        Optional<ApiTokenCredentials> credentials = ApiTokenCredentials.parse(headerValue);
        if (credentials.isEmpty()) {
            return AuthenticationResult.unauthorized();
        }

        Optional<ApiTokenRecord> token = loadToken(credentials.get().tokenId());
        if (token.isEmpty() || !isTokenUsable(token.get())) {
            return AuthenticationResult.unauthorized();
        }

        ApiTokenRecord tokenRecord = token.get();
        if (!secretMatches(credentials.get().secret(), tokenRecord)) {
            return AuthenticationResult.unauthorized();
        }

        if (!isAuthorized(tokenRecord, requestPath, requestMethod)) {
            return AuthenticationResult.forbidden();
        }

        return AuthenticationResult.authenticated(
                new ApiClientPrincipal(tokenRecord.tokenId(), tokenRecord.clientCode()));
    }

    /**
     * BCrypt is deliberately slow (about a second per check in the native image at its CPU
     * limit), and every BFF call pays it, so a secret that already matched is remembered
     * for the cache TTL. The cache key is the token id plus a SHA-256 digest of the
     * presented secret, never the secret itself; a wrong secret is never cached.
     */
    private boolean secretMatches(String presentedSecret, ApiTokenRecord tokenRecord) {
        Duration cacheTtl = properties.getCacheTtl();
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            return passwordEncoder.matches(presentedSecret, tokenRecord.secretHash());
        }

        String cacheKey = tokenRecord.tokenId() + ":" + sha256Hex(presentedSecret);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime verifiedUntil = verifiedSecrets.get(cacheKey);
        if (verifiedUntil != null && verifiedUntil.isAfter(now)) {
            return true;
        }

        if (!passwordEncoder.matches(presentedSecret, tokenRecord.secretHash())) {
            return false;
        }

        if (verifiedSecrets.size() >= MAX_VERIFIED_SECRET_ENTRIES) {
            verifiedSecrets.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        }
        if (verifiedSecrets.size() < MAX_VERIFIED_SECRET_ENTRIES) {
            verifiedSecrets.put(cacheKey, now.plus(cacheTtl));
        }
        return true;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for the verified-secret cache", e);
        }
    }

    private Optional<ApiTokenRecord> loadToken(String tokenId) {
        Duration cacheTtl = properties.getCacheTtl();
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            return repository.findByTokenId(tokenId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        CacheEntry existing = cache.get(tokenId);
        if (existing != null && existing.expiresAt().isAfter(now)) {
            return existing.token();
        }

        Optional<ApiTokenRecord> loaded = repository.findByTokenId(tokenId);
        cache.put(tokenId, new CacheEntry(loaded, now.plus(cacheTtl)));
        return loaded;
    }

    private boolean isTokenUsable(ApiTokenRecord token) {
        return token.active() && !token.isExpired(LocalDateTime.now(clock));
    }

    private boolean isAuthorized(ApiTokenRecord token, String requestPath, String requestMethod) {
        return token.authorizations().stream()
                .filter(authorization -> authorization.matchesMethod(requestMethod))
                .anyMatch(authorization -> pathMatcher.match(authorization.pathPattern(), requestPath));
    }

    private record CacheEntry(Optional<ApiTokenRecord> token, LocalDateTime expiresAt) {
    }

    public record AuthenticationResult(Status status, ApiClientPrincipal principal) {
        public static AuthenticationResult unauthorized() {
            return new AuthenticationResult(Status.UNAUTHORIZED, null);
        }

        public static AuthenticationResult forbidden() {
            return new AuthenticationResult(Status.FORBIDDEN, null);
        }

        public static AuthenticationResult authenticated(ApiClientPrincipal principal) {
            return new AuthenticationResult(Status.AUTHENTICATED, principal);
        }
    }

    public enum Status {
        AUTHENTICATED,
        UNAUTHORIZED,
        FORBIDDEN
    }
}
