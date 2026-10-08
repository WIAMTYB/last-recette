package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExternalServiceRateLimiterTest {

    @Test
    void shouldRejectCallWhenConfiguredRateIsExceeded() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getApiTitres().setMaxCallsPerMinute(1);
        properties.getApiTitres().setRateLimitTimeout(Duration.ZERO);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        ExternalServiceRateLimiter rateLimiter = new ExternalServiceRateLimiter(properties, new PlanMetrics(meterRegistry));

        assertThat(rateLimiter.execute(ExternalServiceRateLimiter.API_TITRES_SERVICE, () -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> rateLimiter.execute(ExternalServiceRateLimiter.API_TITRES_SERVICE, () -> "blocked"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(ex -> assertThat(((ApplicationException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_RATE_LIMITED));
        assertThat(meterRegistry.counter(
                "epargne_external_service_rate_limited_total",
                "service", ExternalServiceRateLimiter.API_TITRES_SERVICE).count())
                .isEqualTo(1);
    }
}
