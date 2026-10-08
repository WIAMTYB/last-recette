package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.AccountRelationship;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AccountsCatalogClientTest {

    @Test
    void shouldValidateMatchingSecAndCashAccounts() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getSecAccountsByCashAccounts(List.of("000123E000123456")))
                .thenReturn(List.of(Map.of(
                        "secAccountID", "0000123456",
                        "cashAccountID", "000123E000123456"
                )));

        AccountsCatalogClient client = new AccountsCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry())
        );

        AccountRelationship relationship = client.findRelationship("0000123456", "000123E000123456");

        assertThat(relationship.valid()).isTrue();
    }

    @Test
    void shouldValidateWhenApiTitresReturnsUppercaseKeys() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getSecAccountsByCashAccounts(List.of("000123E000123456")))
                .thenReturn(List.of(Map.of(
                        "SECACCOUNTID", "0000123456",
                        "CASHACCOUNTID", "000123E000123456"
                )));

        AccountsCatalogClient client = new AccountsCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry())
        );

        AccountRelationship relationship = client.findRelationship("0000123456", "000123E000123456");

        assertThat(relationship.valid()).isTrue();
    }

    @Test
    void shouldRejectUnknownRelationship() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getSecAccountsByCashAccounts(List.of("000123E000123456"))).thenReturn(List.of());

        AccountsCatalogClient client = new AccountsCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry())
        );

        AccountRelationship relationship = client.findRelationship("0000123456", "000123E000123456");

        assertThat(relationship.valid()).isFalse();
    }

    @Test
    void shouldTreatNoContentAsMissingRelationship() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.getSecAccountsByCashAccounts(List.of("000123E000123456")))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.NO_CONTENT,
                        "No Content"
                ));

        AccountsCatalogClient client = new AccountsCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry())
        );

        AccountRelationship relationship = client.findRelationship("0000123456", "000123E000123456");

        assertThat(relationship.valid()).isFalse();
    }
}
