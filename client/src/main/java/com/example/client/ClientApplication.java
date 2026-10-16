package com.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
	RouterFunction<ServerResponse> uiRoutes() {
		return route().GET("/**", http()).before(BeforeFilterFunctions.uri("http://localhost:8020")).build();
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	RouterFunction<ServerResponse> apiRoutes() {
		return route().GET("/api/**", http())
			.before(BeforeFilterFunctions.uri("http://localhost:8081"))
			.before(BeforeFilterFunctions.rewritePath("/api", "/"))
			.filter(TokenRelayFilterFunctions.tokenRelay())
			.build();
	}

}
