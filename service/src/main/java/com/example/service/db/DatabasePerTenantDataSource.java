package com.example.service.db;

import com.example.service.DataSourceInitializer;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.zaxxer.hikari.HikariDataSource;
import io.arconia.multitenancy.core.context.TenantContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

class DatabasePerTenantDataSource extends DelegatingDataSource {

	DatabasePerTenantDataSource(Function<String, DataSource> supplier) {
		var map = new ConcurrentHashMap<String, DataSource>();
		var pfb = new ProxyFactoryBean();
		pfb.addInterface(XADataSource.class);
		pfb.addInterface(DataSource.class);
		pfb.addAdvice((MethodInterceptor) methodInvocation -> {
			var targetDatabase = map.computeIfAbsent(TenantContext.getTenantIdentifier(), supplier);
			return methodInvocation.getMethod().invoke(targetDatabase, methodInvocation.getArguments());
		});
		var db = (DataSource) pfb.getObject();
		setTargetDataSource(db);
	}

}

@Profile("db")
@Configuration
class DatabasePerTenantConfiguration {

	@Bean
	DatabasePerTenantDataSource dataSource(DataSourceInitializer dataSourceInitializer, DatabaseRegistry registry) {
		return new DatabasePerTenantDataSource(tenantId -> {
			var dsi = DataSourceInitializer.caching(dataSourceInitializer);
			var connectionDetails = registry.getConnectionDetails(tenantId);
			var db = DataSourceBuilder.create()
				.url(connectionDetails.getJdbcUrl())
				.username(connectionDetails.getUsername())
				.password(connectionDetails.getPassword())
				.type(HikariDataSource.class)
				.build();
			return dsi.initialize(tenantId, db);
		});
	}

	@Bean
	ZerodepDockerHttpClient zerodepDockerHttpClient(DockerClientConfig config) {
		return new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost())
			.sslConfig(config.getSSLConfig())
			.build();
	}

	@Bean
	DefaultDockerClientConfig dockerClientConfig() {
		return DefaultDockerClientConfig.createDefaultConfigBuilder().build();
	}

	@Bean
	DockerDatabaseRegistry dockerDatabaseRegistry(DockerHttpClient httpClient, DockerClientConfig config) {
		return new DockerDatabaseRegistry(httpClient, config);
	}

}

class DockerDatabaseRegistry implements DatabaseRegistry, InitializingBean {

	private final DockerClientConfig dockerClientConfig;

	private final Map<String, String> mapping = new ConcurrentHashMap<>();

	private final DockerHttpClient dockerHttpClient;

	DockerDatabaseRegistry(DockerHttpClient dockerHttpClient, DockerClientConfig dockerClientConfig) {
		this.dockerClientConfig = dockerClientConfig;
		this.dockerHttpClient = dockerHttpClient;
	}

	private void refresh() throws Exception {

		try (var dockerClient = DockerClientImpl.getInstance(this.dockerClientConfig, this.dockerHttpClient)) {
			var containers = dockerClient.listContainersCmd()
				.withShowAll(false)
				.withLabelFilter(List.of("com.docker.compose.service"))
				.exec();
			for (var container : containers) {
				var labels = container.getLabels();
				var serviceName = labels.get("com.docker.compose.service");
				var ports = container.getPorts();
				for (var port : ports) {
					this.mapping.put(serviceName,
							"jdbc:postgresql://localhost:" + port.getPublicPort() + "/" + serviceName);
				}
			}
		}
	}

	@Override
	public JdbcConnectionDetails getConnectionDetails(String tenantId) {
		if (this.mapping.containsKey(tenantId)) {
			return new JdbcConnectionDetails() {
				@Override
				public @Nullable String getUsername() {
					return "myuser";
				}

				@Override
				public @Nullable String getPassword() {
					return "secret";
				}

				@Override
				public String getJdbcUrl() {
					return mapping.get(tenantId);
				}
			};
		}
		return null;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.refresh();
	}

}

interface DatabaseRegistry {

	JdbcConnectionDetails getConnectionDetails(String tenantId);

}
