package com.example.service.schema;

import com.example.service.DataSourceInitializer;
import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

class SchemaPerTenantDataSource extends DelegatingDataSource {

	private final DataSourceInitializer compositeDataSourceInitializer;

	SchemaPerTenantDataSource(DataSource target, DataSourceInitializer compositeDataSourceInitializer) {
		this.compositeDataSourceInitializer = DataSourceInitializer.caching((tenant, ds) -> {
			var jdbc = JdbcClient.create(ds);
			var sql = " CREATE SCHEMA IF NOT EXISTS " + schemaForTenant();
			jdbc.sql(sql).update();
			IO.println(sql);
			return compositeDataSourceInitializer.initialize(tenant, ds);
		});
		this.setTargetDataSource(target);
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		var connection = super.getConnection(username, password);
		return this.initialize(connection);
	}

	@Override
	public Connection getConnection() throws SQLException {
		var connection = super.getConnection();
		return this.initialize(connection);
	}

	private Connection initialize(Connection connection) throws SQLException {
		var dataSource = new SingleConnectionDataSource(connection, true);
		var schemaName = this.schemaForTenant();
		connection.setSchema(schemaName);
		var tenantIdentifier = "" + TenantContext.getTenantIdentifier();
		compositeDataSourceInitializer.initialize(tenantIdentifier, dataSource);
		return connection;
	}

	private String schemaForTenant() {
		return "schema_" + TenantContext.getTenantIdentifier();
	}

}
