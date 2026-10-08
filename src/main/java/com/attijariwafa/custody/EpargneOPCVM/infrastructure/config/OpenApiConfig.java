package com.attijariwafa.custody.EpargneOPCVM.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiConfig.API_KEY_SECURITY_SCHEME))
@SecurityScheme(
        name = OpenApiConfig.API_KEY_SECURITY_SCHEME,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Api-Key")
public class OpenApiConfig {

    public static final String API_KEY_SECURITY_SCHEME = "ApiKeyAuth";
}
