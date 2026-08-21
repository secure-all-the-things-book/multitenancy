package com.example.service;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class CachingDataSourceInitializer implements DataSourceInitializer {

	private final DataSourceInitializer dataSourceInitializer;

	private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

	public CachingDataSourceInitializer(DataSourceInitializer dataSourceInitializer) {
		this.dataSourceInitializer = dataSourceInitializer;
	}

	@Override
	public DataSource initialize(String tenant, DataSource dataSource) {
		if (!this.cache.computeIfAbsent(tenant, a -> false)) {
			var res = this.dataSourceInitializer.initialize(tenant, dataSource);
			this.cache.put(tenant, true);
			return res;
		}
		return dataSource;
	}

}
