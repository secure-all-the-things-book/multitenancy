package com.example.service;

import javax.sql.DataSource;

public interface DataSourceInitializer {

	DataSource initialize(String tenant, DataSource dataSource);

	static DataSourceInitializer caching(DataSourceInitializer dataSourceInitializer) {
		return new CachingDataSourceInitializer(dataSourceInitializer);
	}

}
