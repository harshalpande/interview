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
}
