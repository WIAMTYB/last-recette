package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ExternalServiceRateLimiter {

    static final String API_TITRES_SERVICE = "api-titres";

    private final Map<String, RateLimiter> rateLimiters = new HashMap<>();
    private final PlanMetrics planMetrics;

    public ExternalServiceRateLimiter(ExternalServiceProperties properties, PlanMetrics planMetrics) {
        this.planMetrics = planMetrics;
        register(API_TITRES_SERVICE, properties.getApiTitres());
    }

    public <T> T execute(String serviceName, Supplier<T> supplier) {
        RateLimiter rateLimiter = rateLimiters.get(serviceName);
        if (rateLimiter == null) {
            return supplier.get();
        }
        try {
            return RateLimiter.decorateSupplier(rateLimiter, supplier).get();
        } catch (RequestNotPermitted ex) {
            planMetrics.recordExternalRateLimited(serviceName);
            throw new ApplicationException(
                    ErrorCode.EXTERNAL_SERVICE_RATE_LIMITED,
                    "Outbound rate limit exceeded for " + serviceName + " service",
                    ex
            );
        }
    }

    private void register(String serviceName, ExternalServiceProperties.ApiTitresDefinition serviceDefinition) {
        if (serviceDefinition.getMaxCallsPerMinute() <= 0) {
            return;
        }

        Duration waitDuration = serviceDefinition.getRateLimitTimeout();
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(serviceDefinition.getMaxCallsPerMinute())
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(waitDuration == null ? Duration.ZERO : waitDuration)
                .build();

        rateLimiters.put(serviceName, RateLimiter.of("clients-" + serviceName, config));
    }
}
