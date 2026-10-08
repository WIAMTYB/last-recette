package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.SrFundAssetResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
class OpcvmCatalogClientTest {
    @Test
    void shouldMapV2FundResponse() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        when(httpApi.listOpcForSrTrade()).thenReturn(List.of(
                new SrFundAssetResponse(
                        "MA0000030520",
                        true,
                        true,
                        new BigDecimal("500"),
                        new BigDecimal("743.5"),
                        "Wafa Oblig Plus"
                )
        ));
        OpcvmCatalogClient client = new OpcvmCatalogClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry())
        );
        FundDetails fund = client.getFund("MA0000030520");
        assertThat(fund.active()).isTrue();
        assertThat(fund.acceptedForScheduledSavings()).isTrue();
        assertThat(fund.latestNav()).isEqualByComparingTo("743.5");
        assertThat(fund.minimumRecurringAmount()).isEqualByComparingTo("500");
        assertThat(fund.name()).isEqualTo("Wafa Oblig Plus");
    }
}