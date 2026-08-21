package com.example.service.db;

import com.example.service.DataSourceInitializer;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

//@Profile("db")
@Configuration
class DataSourceConfiguration {

	@Bean
	ZerodepDockerHttpClient zerodepDockerHttpClient(DockerClientConfig config) {
		return new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost())
			.sslConfig(config.getSSLConfig())
			.build();
	}

	@Bean
	DockerClientConfig dockerClientConfig() {
		return DefaultDockerClientConfig.createDefaultConfigBuilder().build();
	}

	@Bean
	DataSource dataSource(DockerDatabaseRegistry dockerDatabaseRegistry, DataSourceInitializer dataSourceInitializer) {
		var dsi = DataSourceInitializer.caching(dataSourceInitializer);
		var stringDataSourceFunction = (Function<String, DataSource>) tenantId -> {
			var url = dockerDatabaseRegistry.getDatasourceConnectionDetails(tenantId);
			var db = DataSourceBuilder.create()
				.url(url.getJdbcUrl())
				.username(url.getUsername())
				.password(url.getPassword())
				.type(HikariDataSource.class)
				.build();
			dsi.initialize(tenantId, db);
			return db;
		};
		return new DatabasePerTenantDataSource(stringDataSourceFunction);
	}

	@Bean
	DockerDatabaseRegistry registry(DockerHttpClient httpClient, DockerClientConfig dockerClientConfig) {
		return new DockerDatabaseRegistry(httpClient, dockerClientConfig);
	}

}
