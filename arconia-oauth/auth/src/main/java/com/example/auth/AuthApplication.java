package com.example.auth;

import io.arconia.multitenancy.core.tenantdetails.TenantDetailsService;
import io.arconia.multitenancy.details.jdbc.JdbcTenantDetailsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Objects;

@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
        return (http) -> http
                .oauth2AuthorizationServer(a -> a.oidc(Customizer.withDefaults()));
    }

    @Bean
    TenantDetailsService tenantDetailsService(DataSource dataSource) {
        return JdbcTenantDetailsService
                .builder()
                .dataSource(dataSource)
                .build();
    }

    @Bean
    JdbcUserDetailsManager jdbcUserDetailsManager(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }
}

@Component
class TenantOAuth2TokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final JdbcClient db;

    private record UserTenantMapping(String user, String tenant) {
    }

    private final RowMapper<UserTenantMapping> rowMapper = (rs, _) ->
            new UserTenantMapping(
                    rs.getString("users_username"),
                    rs.getString("tenant_details_identifier"));

    TenantOAuth2TokenCustomizer(JdbcClient jdbcClient) {
        this.db = jdbcClient;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        IO.println("customizing token!");
        var principal = context.getPrincipal();
        var tenant = db
                .sql("select * from users_tenant_details utd where utd.users_username = ?")
                .params(Objects.requireNonNull(principal).getName())
                .query(this.rowMapper)
                .single()
                .tenant();
        context.getClaims().claim("tenant", tenant);
    }
}