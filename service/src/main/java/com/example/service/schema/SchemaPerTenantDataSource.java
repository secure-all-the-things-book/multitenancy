package com.example.service.schema;

import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

class SchemaPerTenantDataSource extends DelegatingDataSource {

	SchemaPerTenantDataSource(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return this.doInit(super.getConnection(username, password), schemaForTenant());
	}

	@Override
	public Connection getConnection() throws SQLException {
		return this.doInit(super.getConnection(), schemaForTenant());
	}

	private Connection doInit(Connection connection, String schemaName) throws SQLException {
		try (var connectionDetailsDataSource = new SingleConnectionDataSource(connection, true)) {
			var jdbc = JdbcClient.create(connectionDetailsDataSource);
			jdbc.sql("CREATE SCHEMA IF NOT EXISTS  " + schemaName);
		}
		connection.setSchema(schemaName);
		return connection;
	}

	private static String schemaForTenant() {
		return "schema_" + TenantContext.getTenantIdentifier();
	}

}
