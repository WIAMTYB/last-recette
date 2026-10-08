package com.attijariwafa.custody.EpargneOPCVM.infrastructure.config;

import com.attijariwafa.custody.EpargneOPCVM.security.ApiTokenAuthenticationFilter;
import com.attijariwafa.custody.EpargneOPCVM.security.ApiTokenProperties;
import com.attijariwafa.custody.EpargneOPCVM.security.DocumentationAccessFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(ApiTokenProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<ApiTokenAuthenticationFilter> apiTokenAuthenticationFilterRegistration(
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter) {
        FilterRegistrationBean<ApiTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(apiTokenAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<DocumentationAccessFilter> documentationAccessFilterRegistration(
            DocumentationAccessFilter documentationAccessFilter) {
        FilterRegistrationBean<DocumentationAccessFilter> registration =
                new FilterRegistrationBean<>(documentationAccessFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter,
            DocumentationAccessFilter documentationAccessFilter,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // session stateless
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // OpenShift probes only. Other actuator endpoints require X-Api-Key authorization.
                    auth.requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll();

                    // Allow ERROR dispatch to reach the error handler.
                    auth.requestMatchers("/error").permitAll();

                    if (apiDocsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**").permitAll();
                    }
                    if (swaggerUiEnabled) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui", "/swagger-ui/**").permitAll();
                    }

                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(documentationAccessFilter, ApiTokenAuthenticationFilter.class)
                // Configure headers
                .headers(headers -> headers
                        // disable frame
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        // X-XSS-Protection mainly browser sec
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none';" +
                                        // allow API calls
                                        "connect-src 'self';" +
                                        // directive required only for Swagger UI js
                                        "script-src 'self' 'unsafe-inline' 'unsafe-eval';" +
                                        // directive required only for Swagger UI styles
                                        "style-src 'self' 'unsafe-inline';" +
                                        // directive required only for Swagger UI fonts
                                        "font-src 'self' data:;" +
                                        // directive required only for Swagger UI images
                                        "img-src 'self' data:;" +
                                        // allow workers
                                        "worker-src 'self';"))
                        // hsts only available in browser sec and remember policy 1year
                        .httpStrictTransportSecurity(hsts ->
                                hsts.maxAgeInSeconds(31536000)))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins with "*"
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        // Allow common headers
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "X-Api-Key",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));
        // Expose response headers that clients might need
        configuration.setExposedHeaders(Arrays.asList(
                "X-Total-Count",
                "Content-Disposition"
        ));
        // Cache preflight requests for 1 hour (3600 seconds)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
