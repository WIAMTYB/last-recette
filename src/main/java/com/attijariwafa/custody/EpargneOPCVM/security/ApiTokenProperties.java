package com.attijariwafa.custody.EpargneOPCVM.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.api-token")
public class ApiTokenProperties {
    private String headerName = "X-Api-Key";
    private Duration cacheTtl = Duration.ofSeconds(60);

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
}

