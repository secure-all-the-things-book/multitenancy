package com.example.service;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface DataSourceInitializer {

	DataSource initialize(String tenantId, DataSource dataSource);

	static DataSourceInitializer caching(DataSourceInitializer initializer) {
		return new CachingDataSourceInitializer(initializer);
	}

}

class CachingDataSourceInitializer implements DataSourceInitializer {

	private final Map<String, Boolean> initialized = new ConcurrentHashMap<>();

	private final DataSourceInitializer dataSourceInitializer;

	CachingDataSourceInitializer(DataSourceInitializer dataSourceInitializer) {
		this.dataSourceInitializer = dataSourceInitializer;
	}

	@Override
	public DataSource initialize(String tenantId, DataSource dataSource) {
		var initialized = this.initialized.computeIfAbsent(tenantId, k -> false);
		if (!initialized) {
			this.dataSourceInitializer.initialize(tenantId, dataSource);
			this.initialized.put(tenantId, true);
		}
		return dataSource;
	}

}

@Component
class DefaultDataSourceInitializer implements DataSourceInitializer {

	@Override
	public DataSource initialize(String tenantId, DataSource dataSource) {
		Flyway.configure(getClass().getClassLoader())
			.dataSource(dataSource)
			.locations("classpath:db/tenants/common", "classpath:db/tenants/" + tenantId + "/")
			.load()
			.migrate();
		return dataSource;
	}

}
