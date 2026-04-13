package com.altimetrik.interview.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration for API documentation.
 * Accessible at http://localhost:8080/api/swagger-ui.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        Info info = new Info()
                .title("Interview Platform API")
                .version("1.0.0")
                .description("REST API for online coding interviews with session management, code compilation, and execution")
                .contact(new Contact()
                        .name("Altimetrik Interview Platform")
                        .email("support@example.com"));

        return new OpenAPI()
                .info(info);
    }
}
