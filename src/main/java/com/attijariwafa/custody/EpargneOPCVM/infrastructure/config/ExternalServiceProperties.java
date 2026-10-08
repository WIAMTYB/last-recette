package com.attijariwafa.custody.EpargneOPCVM.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clients")
public class ExternalServiceProperties {

    private ApiTitresDefinition apiTitres = new ApiTitresDefinition();

    public ApiTitresDefinition getApiTitres() {
        return apiTitres;
    }

    public void setApiTitres(ApiTitresDefinition apiTitres) {
        this.apiTitres = apiTitres;
    }

    public static class ApiTitresDefinition {
        private String baseUrl = "http://localhost:8081";
        private String apiKey = "";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxCallsPerMinute;
        private Duration rateLimitTimeout = Duration.ZERO;
        private boolean balanceCheckEnabled;
        private String balanceCaller = "EPG";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getMaxCallsPerMinute() {
            return maxCallsPerMinute;
        }

        public void setMaxCallsPerMinute(int maxCallsPerMinute) {
            this.maxCallsPerMinute = maxCallsPerMinute;
        }

        public Duration getRateLimitTimeout() {
            return rateLimitTimeout;
        }

        public void setRateLimitTimeout(Duration rateLimitTimeout) {
            this.rateLimitTimeout = rateLimitTimeout;
        }

        public boolean isBalanceCheckEnabled() {
            return balanceCheckEnabled;
        }

        public void setBalanceCheckEnabled(boolean balanceCheckEnabled) {
            this.balanceCheckEnabled = balanceCheckEnabled;
        }

        public String getBalanceCaller() {
            return balanceCaller;
        }

        public void setBalanceCaller(String balanceCaller) {
            this.balanceCaller = balanceCaller;
        }
    }
}
