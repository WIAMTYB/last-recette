package com.attijariwafa.custody.EpargneOPCVM.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DocumentationAccessFilter extends OncePerRequestFilter {
    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;

    public DocumentationAccessFilter(@Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
                                     @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled) {
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerUiEnabled = swaggerUiEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = requestPath(request);
        if ((!apiDocsEnabled && isApiDocsPath(path)) || (!swaggerUiEnabled && isSwaggerUiPath(path))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private static boolean isApiDocsPath(String path) {
        return "/v3/api-docs".equals(path) || path.startsWith("/v3/api-docs/");
    }

    private static boolean isSwaggerUiPath(String path) {
        return "/swagger-ui".equals(path) || "/swagger-ui.html".equals(path) || path.startsWith("/swagger-ui/");
    }
}
