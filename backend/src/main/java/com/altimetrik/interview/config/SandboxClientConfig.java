package com.altimetrik.interview.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class SandboxClientConfig {

    @Bean
    @Qualifier("sandboxRestClient")
    public RestClient sandboxRestClient(RestClient.Builder restClientBuilder,
                                        @Value("${sandbox.base-url:http://localhost:8082/api}") String sandboxBaseUrl) {
        return restClientBuilder
                .baseUrl(sandboxBaseUrl)
                .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                .build();
    }

    @Bean
    @Qualifier("sandboxFrontendRestClient")
    public RestClient sandboxFrontendRestClient(RestClient.Builder restClientBuilder,
                                                @Value("${sandbox.frontend-base-url:http://localhost:8083/api}") String sandboxFrontendBaseUrl) {
        return restClientBuilder
                .baseUrl(sandboxFrontendBaseUrl)
                .defaultHeaders(headers -> headers.setContentType(MediaType.APPLICATION_JSON))
                .build();
    }
}
