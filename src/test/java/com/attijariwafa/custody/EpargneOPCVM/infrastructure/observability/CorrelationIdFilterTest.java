package com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void shouldUseInboundCorrelationIdAndClearMdcAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER_NAME, "corr-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValueDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcValueDuringRequest.set(MDC.get(CorrelationIds.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIds.HEADER_NAME)).isEqualTo("corr-123");
        assertThat(mdcValueDuringRequest).hasValue("corr-123");
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValueDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcValueDuringRequest.set(MDC.get(CorrelationIds.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIds.HEADER_NAME)).isNotBlank();
        assertThat(mdcValueDuringRequest.get()).isEqualTo(response.getHeader(CorrelationIds.HEADER_NAME));
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).isNull();
    }
}
