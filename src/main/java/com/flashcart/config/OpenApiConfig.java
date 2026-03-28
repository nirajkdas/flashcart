package com.flashcart.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlashCart API")
                        .description("""
                                ## Real-time Flash Sale E-commerce Engine
                                
                                Built with Spring Boot 3 · PostgreSQL · Redis · WebSocket (STOMP)
                                
                                ### Key Features
                                - **JWT Authentication** — Register/login to get a Bearer token
                                - **Flash Sale Purchase** — Atomic Redis DECR + JPA optimistic locking prevent overselling
                                - **Real-time Inventory** — WebSocket broadcasts stock changes live
                                - **Rate Limiting** — Redis sliding-window per user/action
                                - **Caching** — Redis cache with per-cache TTL for products/categories/sales
                                
                                ### WebSocket
                                Connect: `ws://localhost:8080/ws` (SockJS)  
                                Subscribe: `/topic/flash-sales/{id}/inventory` for live stock countdown
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("FlashCart").email("admin@flashcart.com")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT token from /api/auth/login")));
    }
}
