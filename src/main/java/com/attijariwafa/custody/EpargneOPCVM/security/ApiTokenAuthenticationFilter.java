package com.attijariwafa.custody.EpargneOPCVM.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"));

    private final ApiTokenAuthenticationService authenticationService;
    private final ApiTokenProperties properties;
    private final boolean apiDocsEnabled;
    private final boolean swaggerUiEnabled;

    @Autowired
    public ApiTokenAuthenticationFilter(ApiTokenAuthenticationService authenticationService,
                                        ApiTokenProperties properties,
                                        @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
                                        @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled) {
        this.authenticationService = authenticationService;
        this.properties = properties;
        this.apiDocsEnabled = apiDocsEnabled;
        this.swaggerUiEnabled = swaggerUiEnabled;
    }

    ApiTokenAuthenticationFilter(ApiTokenAuthenticationService authenticationService,
                                 ApiTokenProperties properties) {
        this(authenticationService, properties, false, false);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return CorsUtils.isPreFlightRequest(request)
                || "/actuator/health/liveness".equals(path)
                || "/actuator/health/readiness".equals(path)
                || (apiDocsEnabled && isApiDocsPath(path))
                || (swaggerUiEnabled && isSwaggerUiPath(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ApiTokenAuthenticationService.AuthenticationResult result = authenticationService.authenticate(
                request.getHeader(properties.getHeaderName()),
                requestPath(request),
                request.getMethod());

        if (result.status() == ApiTokenAuthenticationService.Status.UNAUTHORIZED) {
            SecurityContextHolder.clearContext();
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"epargne-programmee\"");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key");
            return;
        }
        if (result.status() == ApiTokenAuthenticationService.Status.FORBIDDEN) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpStatus.FORBIDDEN.value(), "API key is not authorized for this resource");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                result.principal(),
                null,
                AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
