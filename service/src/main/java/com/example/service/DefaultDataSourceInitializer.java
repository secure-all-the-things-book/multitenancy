package com.example.service;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class DefaultDataSourceInitializer implements DataSourceInitializer {

	@Override
	public DataSource initialize(String tenant, DataSource dataSource) {
		Flyway.configure(getClass().getClassLoader())
			.dataSource(dataSource)
			.locations(new String[] { "classpath:db/tenants/common", "classpath:db/tenants/" + tenant + "/" })
			.load()
			.migrate();
		return dataSource;
	}

}
