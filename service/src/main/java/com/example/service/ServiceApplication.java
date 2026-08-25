package com.example.service;

import io.arconia.multitenancy.core.context.TenantContext;
import io.arconia.multitenancy.web.context.resolvers.OAuth2TenantResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

@SpringBootApplication
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

    @Bean
    OAuth2TenantResolver oauth2TenantResolver() {
        return OAuth2TenantResolver.builder().tenantClaimName("tenant").build();
    }
}

// db for each tenant
// schema for each tenant
// row for each tenant (Row Level Security: PostgreSQL, SQL Server, Oracle)

@Controller
@ResponseBody
class CustomerController {

    private final CustomerRepository repository;

    CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/customers")
    Collection<Customer> customers() {
        return repository.findAll();
    }
}

record Customer(int id, String name) {
}

@Repository
class CustomerRepository {

    private final JdbcClient db;

    CustomerRepository(JdbcClient db) {
        this.db = db;
    }

    Collection<Customer> findAll() {
        return this.db
                .sql("select * from customer")
                .query((rs, _) -> new Customer(rs.getInt("id"), rs.getString("name")))
                .list();
    }
}


@Controller
@ResponseBody
class TenantController {

    @GetMapping("/")
    Map<String, String> me() {
        return Map.of("tenant", TenantContext.getTenantIdentifier(),
                "user", SecurityContextHolder.getContextHolderStrategy()
                        .getContext()
                        .getAuthentication()
                        .getName());
    }
}