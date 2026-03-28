package com.flashcart;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@OpenAPIDefinition(info = @Info(
        title       = "FlashCart API",
        version     = "1.0.0",
        description = "Real-time Flash Sale E-commerce Engine — Spring Boot + PostgreSQL + Redis + WebSocket"
))
public class FlashCartApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashCartApplication.class, args);
    }
}
