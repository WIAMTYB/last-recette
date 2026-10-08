package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmission;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmissionResult;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.OrderAckResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.SrOrderRequest;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OrderCreationClientTest {

    @Test
    void shouldBookSubscriptionOrderThroughApiTitres() {
        ApiTitresHttpApi httpApi = Mockito.mock(ApiTitresHttpApi.class);
        ArgumentCaptor<SrOrderRequest> requestCaptor = ArgumentCaptor.forClass(SrOrderRequest.class);
        when(httpApi.bookSrOrder(requestCaptor.capture()))
                .thenReturn(new OrderAckResponse(200, null, "ORD-123"));

        OrderCreationClient client = new OrderCreationClient(
                httpApi,
                new ExternalServiceRateLimiter(new ExternalServiceProperties(), new PlanMetrics(new SimpleMeterRegistry())),
                new PlanMetrics(new SimpleMeterRegistry())
        );

        OrderSubmissionResult result = client.createOrder(new OrderSubmission(
                "EP000001",
                "EP000001:2026-07-10",
                LocalDate.of(2026, 7, 10),
                "0000123456",
                "000123E000123456",
                "MA0000000001",
                true,
                new BigDecimal("500"),
                null
        ));

        SrOrderRequest request = requestCaptor.getValue();
        assertThat(request.clientReference()).isEqualTo("EP00000120260710");
        assertThat(request.sRType()).isEqualTo("Subscription");
        assertThat(request.byAmount()).isEqualTo("true");
        assertThat(request.source()).isEqualTo("EPG");
        assertThat(result.accepted()).isTrue();
        assertThat(result.orderReference()).isEqualTo("ORD-123");
    }
}
