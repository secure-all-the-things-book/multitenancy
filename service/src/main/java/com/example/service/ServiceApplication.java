package com.example.service;

import io.arconia.multitenancy.web.context.resolvers.OAuth2TenantResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

@ImportRuntimeHints(Hints.class)
@SpringBootApplication
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

    @Bean
    OAuth2TenantResolver oauth2TenantResolver() {
        return OAuth2TenantResolver
                .builder()
                .tenantClaimName("tenant")
                .build();
    }
}


