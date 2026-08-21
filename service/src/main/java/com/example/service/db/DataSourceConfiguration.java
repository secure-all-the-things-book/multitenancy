package com.example.service.db;

import com.example.service.TenantAwareDataSourceSupplier;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.function.Function;

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
	TenantAwareDataSourceSupplier tenantAwareDataSourceSupplier(DockerDatabaseRegistry dockerDatabaseRegistry) {
		return _ -> {
			var stringDataSourceFunction = (Function<String, DataSource>) tenantId -> {
				var url = dockerDatabaseRegistry.get(tenantId);
				return DataSourceBuilder.create()
					.url(url.getJdbcUrl())
					.username(url.getUsername())
					.password(url.getPassword())
					.type(HikariDataSource.class)
					.build();
			};
			return new DataSourcePerTenantDataSource(stringDataSourceFunction);
		};
	}

	@Bean
	DockerDatabaseRegistry registry(DockerHttpClient httpClient, DockerClientConfig dockerClientConfig) {
		return new DockerDatabaseRegistry(httpClient, dockerClientConfig);
	}

}
