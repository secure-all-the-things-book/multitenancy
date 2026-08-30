package com.example.service.schema;

import com.example.service.DataSourceInitializer;
import com.example.service.DataSourceInitializers;
import io.arconia.multitenancy.core.context.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
class SchemaPerTenantDataSourceConfiguration {

	@Bean
	static SchemaPerTenantDataSourceBeanPostProcessor schemaPerTenantDataSourceBeanPostProcessor() {
		return new SchemaPerTenantDataSourceBeanPostProcessor();
	}

	static class SchemaPerTenantDataSourceBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

		private final AtomicReference<ObjectProvider<DataSourceInitializer>> dataSourceInitializer = new AtomicReference<>();

		@Override
		public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof DataSource dataSource) {
				return new SchemaPerTenantDataSource(dataSource, this.dataSourceInitializer.get().getIfAvailable());
			}
			return bean;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
			this.dataSourceInitializer.set(beanFactory.getBeanProvider(DataSourceInitializer.class));
		}

	}

}

class SchemaPerTenantDataSource extends DelegatingDataSource {

	private final DataSourceInitializer dataSourceInitializer;

	SchemaPerTenantDataSource(DataSource dataSource, DataSourceInitializer dbi) {
		this.dataSourceInitializer = DataSourceInitializers.caching((tenantId, dataSource1) -> {
			var jdbc = JdbcClient.create(dataSource1);
			jdbc.sql("create schema if not exists " + schema()).update();
			return dbi.initialize(tenantId, dataSource1);
		});
		this.setTargetDataSource(dataSource);
	}

	private String schema() {
		return "schema_" + TenantContext.getTenantIdentifier();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return this.initialize(super.getConnection(username, password));
	}

	@Override
	public Connection getConnection() throws SQLException {
		return this.initialize(super.getConnection());
	}

	private Connection initialize(Connection connection) throws SQLException {
		var tenantIdentifier = TenantContext.getTenantIdentifier();
		connection.setSchema(schema());
		var singleConnectionDataSource = new SingleConnectionDataSource(connection, true);
		this.dataSourceInitializer.initialize(tenantIdentifier, singleConnectionDataSource);
		return connection;
	}

}
