package com.example.service;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class DefaultDataSourceInitializer implements DataSourceInitializer {

	private final Map<String, Boolean> initialized = new ConcurrentHashMap<>();

	@Override
	public DataSource initialize(String tenant, DataSource dataSource) {
		if (!this.initialized.computeIfAbsent(tenant, key -> false)) {
			Flyway.configure(getClass().getClassLoader())
				.dataSource(dataSource)
				.locations(new String[] { "classpath:db/tenants/common", "classpath:db/tenants/" + tenant + "/" })
				.load()
				.migrate();
			this.initialized.put(tenant, true);
		}
		return dataSource;
	}

}
