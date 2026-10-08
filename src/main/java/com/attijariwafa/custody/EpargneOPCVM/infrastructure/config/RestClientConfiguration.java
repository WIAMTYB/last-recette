package com.attijariwafa.custody.EpargneOPCVM.infrastructure.config;

import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@ImportRuntimeHints(NativeRuntimeHints.class)
public class RestClientConfiguration {

    @Bean
    ApiTitresHttpApi apiTitresHttpApi(ExternalServiceProperties properties) {
        ExternalServiceProperties.ApiTitresDefinition apiTitres = properties.getApiTitres();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(apiTitres.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(apiTitres.getReadTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiTitres.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> applySecurityHeaders(headers, apiTitres))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(CorrelationIds.HEADER_NAME, CorrelationIds.currentOrCreate());
                    return execution.execute(request, body);
                });

        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build();
        return factory.createClient(ApiTitresHttpApi.class);
    }

    private void applySecurityHeaders(HttpHeaders headers, ExternalServiceProperties.ApiTitresDefinition apiTitres) {
        if (!apiTitres.getApiKey().isBlank()) {
            headers.set("X-Api-Key", apiTitres.getApiKey());
        }
    }
}
