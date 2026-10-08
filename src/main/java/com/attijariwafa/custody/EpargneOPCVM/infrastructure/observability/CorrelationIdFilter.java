package com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability;

import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = CorrelationIds.establish(request.getHeader(CorrelationIds.HEADER_NAME));
        response.setHeader(CorrelationIds.HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
