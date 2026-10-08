package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExternalServiceClientMetricsTest {

    @Test
    void shouldRecordExternalServiceSuccessLatency() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getSecAccountsByCashAccounts(List.of("CASH")))
                .thenReturn(List.of(Map.of("secAccountID", "SEC", "cashAccountID", "CASH")));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PlanMetrics planMetrics = new PlanMetrics(meterRegistry);
        AccountsCatalogClient client = new AccountsCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), planMetrics),
                planMetrics
        );

        client.findRelationship("SEC", "CASH");

        assertThat(meterRegistry.counter(
                "epargne_external_service_calls_total",
                "service", "accounts-relationship",
                "outcome", "success").count()).isEqualTo(1);
        assertThat(meterRegistry.find("epargne_external_service_latency")
                .tag("service", "accounts-relationship")
                .tag("outcome", "success")
                .timer()).isNotNull();
    }

    @Test
    void shouldRecordExternalServiceFailureLatency() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getSecAccountsByCashAccounts(List.of("CASH"))).thenThrow(new IllegalStateException("down"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PlanMetrics planMetrics = new PlanMetrics(meterRegistry);
        AccountsCatalogClient client = new AccountsCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), planMetrics),
                planMetrics
        );

        assertThatThrownBy(() -> client.findRelationship("SEC", "CASH")).isInstanceOf(ApplicationException.class);

        assertThat(meterRegistry.counter(
                "epargne_external_service_calls_total",
                "service", "accounts-relationship",
                "outcome", "failure").count()).isEqualTo(1);
    }
}
