package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.CashBalance;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.CashBalanceRequest;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.CashBalanceResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CashBalanceClientTest {

    @Test
    void shouldSkipBalanceCheckByDefault() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        CashBalanceClient client = new CashBalanceClient(
                Mockito.mock(ApiTitresHttpApi.class),
                properties,
                new ExternalServiceRateLimiter(properties, new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry()));

        CashBalance balance = client.getBalance("000123E000123456");

        assertThat(balance.cashAccount()).isEqualTo("000123E000123456");
        assertThat(balance.availableAmount()).isPositive();
    }

    @Test
    void shouldUseSoldeDisponibleWhenBalanceCheckEnabled() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        ArgumentCaptor<CashBalanceRequest> requestCaptor = ArgumentCaptor.forClass(CashBalanceRequest.class);
        when(httpApi.getCashBalance(requestCaptor.capture()))
                .thenReturn(new CashBalanceResponse(0, "OK", new BigDecimal("150050")));

        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getApiTitres().setBalanceCheckEnabled(true);
        properties.getApiTitres().setBalanceCaller("EPG");

        CashBalanceClient client = new CashBalanceClient(
                httpApi,
                properties,
                new ExternalServiceRateLimiter(properties, new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry()));

        CashBalance balance = client.getBalance("001031000610865");

        CashBalanceRequest request = requestCaptor.getValue();
        assertThat(request.caller()).isEqualTo("EPG");
        assertThat(request.compte()).isEqualTo("001031000610865");
        assertThat(request.correlationId()).isNotBlank();
        assertThat(balance.availableAmount()).isEqualByComparingTo("1500.50");
    }

    @Test
    void shouldConvertSoldeDisponibleFromCentimesToDirhams() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getCashBalance(any()))
                .thenReturn(new CashBalanceResponse(0, "OK", new BigDecimal("-2559156")));

        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getApiTitres().setBalanceCheckEnabled(true);

        CashBalanceClient client = new CashBalanceClient(
                httpApi,
                properties,
                new ExternalServiceRateLimiter(properties, new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry()));

        CashBalance balance = client.getBalance("001031000610865");

        assertThat(balance.availableAmount()).isEqualByComparingTo("-25591.56");
    }

    @Test
    void shouldFailWithErrorMessageWhenReturnCodeIsNotZero() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getCashBalance(any()))
                .thenReturn(new CashBalanceResponse(1, "Compte inconnu", null));

        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getApiTitres().setBalanceCheckEnabled(true);

        CashBalanceClient client = new CashBalanceClient(
                httpApi,
                properties,
                new ExternalServiceRateLimiter(properties, new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry()));

        assertThatThrownBy(() -> client.getBalance("001031000610865"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Compte inconnu");
    }

    @Test
    void shouldPassCashAccountAsProvidedForApiTitresNormalization() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getCashBalance(any()))
                .thenReturn(new CashBalanceResponse(0, "OK", new BigDecimal("10000")));

        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getApiTitres().setBalanceCheckEnabled(true);

        CashBalanceClient client = new CashBalanceClient(
                httpApi,
                properties,
                new ExternalServiceRateLimiter(properties, new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry()));

        client.getBalance("000949V000301233");

        ArgumentCaptor<CashBalanceRequest> requestCaptor = ArgumentCaptor.forClass(CashBalanceRequest.class);
        verify(httpApi).getCashBalance(requestCaptor.capture());
        assertThat(requestCaptor.getValue().compte()).isEqualTo("000949V000301233");
    }
}
