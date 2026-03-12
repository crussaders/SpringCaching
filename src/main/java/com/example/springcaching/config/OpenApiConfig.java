package com.example.springcaching.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc / Swagger UI configuration.
 * Access the UI at http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI springCachingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Caching Demo API")
                        .description("""
                                Demonstrates all caching strategies available in Spring Boot:
                                - **Caffeine** (local, in-process, primary cache manager)
                                - **EhCache** (local, JSR-107 compliant)
                                - **Redis** (distributed, out-of-process)
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Demo"))
                        .license(new License().name("Apache 2.0")));
    }
}
