package com.example.database_per_tenant;

import com.zaxxer.hikari.HikariDataSource;
import io.arconia.multitenancy.core.context.TenantContext;
import io.arconia.multitenancy.core.tenantdetails.Tenant;
import io.arconia.multitenancy.core.tenantdetails.TenantDetails;
import io.arconia.multitenancy.core.tenantdetails.TenantDetailsService;
import org.aopalliance.intercept.MethodInterceptor;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.lang.annotation.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@SpringBootApplication
public class DatabasePerTenantApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatabasePerTenantApplication.class, args);
    }
}

@Configuration
class TenantDataSourceConfiguration {

    private DataSource build(int port) {
        return DataSourceBuilder
                .create()
                .type(HikariDataSource.class)
                .url("jdbc:postgresql://localhost:" + port + "/mydatabase")
                .username("myuser")
                .password("secret")
                .build();
    }

    @Bean
    JdbcTenantDetailsService detailsService(@AdminDataSource DataSource adminDataSource) {
        return new JdbcTenantDetailsService(adminDataSource);
    }

    @Bean
    @FlywayDataSource
    @AdminDataSource
    DataSource adminDataSource() {
        return this.build(5432);
    }

    @Bean
    @Primary
    DataSourcePerTenantDataSource tenantDataSource(@AdminDataSource DataSource adminDataSource) {
        return new DataSourcePerTenantDataSource(adminDataSource, tenantIdentifier -> {
            var db = build(4000 + Integer.parseInt(tenantIdentifier));
            Flyway.configure(getClass().getClassLoader())
                    .dataSource(db)
                    .locations("classpath:db/tenants/common", "classpath:db/tenants/t" + tenantIdentifier)
                    .load()
                    .migrate();
            return db;
        });
    }
}

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier("adminDataSource")
@interface AdminDataSource {
    String value() default "";
}

class DataSourcePerTenantDataSource extends DelegatingDataSource {

    DataSourcePerTenantDataSource(DataSource adminDataSource,
                                  Function<String, DataSource> supplier) {
        var tenants = new ConcurrentHashMap<String, DataSource>();
        var pfb = new ProxyFactoryBean();
        pfb.addInterface(XADataSource.class);
        pfb.addInterface(DataSource.class);
        pfb.addAdvice((MethodInterceptor) invocation -> {
            var tenantIdentifier = TenantContext.getTenantIdentifier();
            var db = !StringUtils.hasText(tenantIdentifier) ?
                    adminDataSource :
                    tenants.computeIfAbsent(tenantIdentifier, supplier);
            return invocation.getMethod().invoke(db, invocation.getArguments());
        });
        var db = (DataSource) pfb.getObject();
        this.setTargetDataSource(db);
    }
}

@Controller
@ResponseBody
class CustomerController {

    private final CustomerRepository repository;

    CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/customers")
    Collection<Customer> customers() {
        return this.repository.findAll();
    }
}

@Table("customers")
record Customer(@Id int id, String name) {
}

interface CustomerRepository extends ListCrudRepository<Customer, Integer> {
}

@Controller
@ResponseBody
class TenantController {

    @GetMapping("/")
    Map<String, String> tenant() {
        var tenantIdentifier = TenantContext.getTenantIdentifier();
        return Map.of("tenant", tenantIdentifier);
    }
}


class JdbcTenantDetailsService implements TenantDetailsService {

    private final ResultSetExtractor<List<TenantDetails>> resultSetExtractor = rs -> {
        var tenants = new HashMap<String, Tenant.Builder>();
        while (rs.next()) {
            var identifier = rs.getString("identifier");
            var enabled = rs.getBoolean("enabled");
            var attributeName = rs.getString("attribute_name");
            var attributeValue = rs.getString("attribute_value");
            var tenant = tenants.computeIfAbsent(identifier, _ -> Tenant.builder().identifier(identifier).enabled(enabled));
            if (StringUtils.hasText(attributeName) && StringUtils.hasText(attributeValue))
                tenant.addAttribute(attributeName, attributeValue);
        }
        return tenants
                .values()
                .stream()
                .map(t -> (TenantDetails) t.build())
                .toList();
    };

    private final String sql = """
            select
                *
            from
              tenant_details td
            left join tenant_details_attributes tda on
                td.id = tda.tenant_id
            """;

    private final JdbcClient db;

    JdbcTenantDetailsService(DataSource db) {
        this.db = JdbcClient.create(db);
    }

    @Override
    public List<? extends TenantDetails> loadAllTenants() {
        return db.sql(sql).query(resultSetExtractor);
    }

    @Override
    public @Nullable TenantDetails loadTenantByIdentifier(String identifier) {
        var all = db.sql(sql + " where td.identifier = ?").params(identifier).query(resultSetExtractor);
        return all.isEmpty() ? null : all.getFirst();
    }
}