package com.example.client;

import io.arconia.multitenancy.web.context.resolvers.OAuth2TenantResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@SpringBootApplication
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientApplication.class, args);
    }

    @Bean
    OAuth2TenantResolver oAuth2TenantResolver() {
        return OAuth2TenantResolver.builder().tenantClaimName("tenant").build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    RouterFunction<ServerResponse> uiRoute() {
        return route()
                .GET("/**", http())
                .before(BeforeFilterFunctions.uri("http://localhost:8020"))
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    RouterFunction<ServerResponse> apiRoute() {
        return route()
                .GET("/api/**", http())
                .before(BeforeFilterFunctions.uri("http://localhost:8081"))
                .before(BeforeFilterFunctions.rewritePath("/api", "/"))
                .filter(TokenRelayFilterFunctions.tokenRelay())
                .build();
    }

}

// :8080/index.html          => :8020/index.html
// :8080/api/customers**     => :8081/customers