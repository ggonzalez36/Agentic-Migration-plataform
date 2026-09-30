package com.enterprise.agentops.infrastructure.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Configuration
public class AgentClientConfig {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";
    public static final String MDC_TRACE_ID_KEY = "traceId";

    @Bean
    public RestClient agentEngineRestClient(AgentClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        ClientHttpRequestInterceptor tracingInterceptor = (request, body, execution) -> {
            // Propagar Correlation ID y Trace ID al motor de agentes en Python
            String correlationId = MDC.get(MDC_CORRELATION_ID_KEY);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            request.getHeaders().set(CORRELATION_ID_HEADER, correlationId);

            String traceId = MDC.get(MDC_TRACE_ID_KEY);
            if (traceId != null && !traceId.isBlank()) {
                request.getHeaders().set(TRACE_ID_HEADER, traceId);
            }

            // Inyección de token de seguridad mTLS o API Key
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
            return execution.execute(request, body);
        };

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(tracingInterceptor)
                .build();
    }
}
