package com.example.schema_per_tenant;

import io.arconia.multitenancy.core.context.TenantContext;
import io.arconia.multitenancy.core.context.events.TenantContextAttachedEvent;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class SchemaPerTenantApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchemaPerTenantApplication.class, args);
	}

	@Bean
	static SchemaPerTenantDataSourcePostProcessor dataSourcePostProcessor() {
		return new SchemaPerTenantDataSourcePostProcessor();
	}

}

@Controller
@ResponseBody
class CustomersController {

	private final JdbcClient db;

	CustomersController(JdbcClient db) {
		this.db = db;
	}

	@GetMapping("/customers")
	Collection<Customer> customers() {
		return this.db.sql("select * from customer").query(Customer.class).list();
	}

}

record Customer(String name, int id) {
}

class SchemaPerTenantDataSourcePostProcessor implements BeanPostProcessor {

	@Override
	public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof DataSource dataSource) {
			return new SchemaPerTenantDataSource(dataSource);
		}
		return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
	}

}

@Component
class SchemaTenantContextAttachedEventListener {

	private final DataSource dataSource;

	private final Map<String, Boolean> tenantsInitialized = new ConcurrentHashMap<>();

	SchemaTenantContextAttachedEventListener(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@EventListener
	void on(TenantContextAttachedEvent tca) {
		if (!this.tenantsInitialized.computeIfAbsent(tca.getTenantIdentifier(), k -> false)) {
			Flyway.configure(getClass().getClassLoader())
				.locations("classpath:db/tenants/migration/common",
						"classpath:db/tenants/migration/s" + tca.getTenantIdentifier())
				.dataSource(this.dataSource)
				.load()
				.migrate();
			this.tenantsInitialized.put(tca.getTenantIdentifier(), true);
		}
	}

}

class SchemaPerTenantDataSource extends DelegatingDataSource {

	SchemaPerTenantDataSource(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		var connection = super.getConnection(username, password);
		connection.setSchema(schemaForTenant());
		return connection;
	}

	@Override
	public Connection getConnection() throws SQLException {
		var connection = super.getConnection();
		connection.setSchema(schemaForTenant());
		return connection;
	}

	private static String schemaForTenant() {
		return "schema_" + TenantContext.getTenantIdentifier();
	}

}