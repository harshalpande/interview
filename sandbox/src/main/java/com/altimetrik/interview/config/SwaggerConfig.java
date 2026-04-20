package com.altimetrik.interview.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI sandboxOpenAPI() {
        Info info = new Info()
                .title("Interview Sandbox API")
                .version("1.0.0")
                .description("REST API for compile/run sandbox execution")
                .contact(new Contact()
                        .name("Altimetrik Interview Platform")
                        .email("support@example.com"));

        return new OpenAPI()
                .info(info);
    }
}
