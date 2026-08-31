package com.example.client;

import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration
class GatewayConfiguration {

    // <.>
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    RouterFunction<ServerResponse> uiRoute() {
        return route()//
                .GET("/**", http()).before(BeforeFilterFunctions.uri("http://localhost:8020"))//
                .build();
    }

    // <.>
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    RouterFunction<ServerResponse> apiRoute() {
        return route()
                .GET("/api/**", http())//
                .before(BeforeFilterFunctions.uri("http://localhost:8081"))//
                .before(BeforeFilterFunctions.rewritePath("/api", "/"))//
                .filter(TokenRelayFilterFunctions.tokenRelay())//
                .build();
    }

}
