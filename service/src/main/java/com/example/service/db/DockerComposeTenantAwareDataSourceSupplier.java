package com.example.service.db;

import com.example.service.TenantAwareDataSourceSupplier;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * this returns a single {@link DataSource db} that in turn loads its DataSources
 * dynamically from a registry populated by information from the local Docker instance. We
 * could as easily use soemthing like Eureka, Consul, LDAP, etc.
 */
@Component
class DockerComposeTenantAwareDataSourceSupplier implements TenantAwareDataSourceSupplier, InitializingBean {

	private final Map<String, String> registry = new ConcurrentHashMap<>();

	private final DockerHttpClient dockerHttpClient;

	private final DockerClientConfig dockerClientConfig;

	private final DataSourcePerTenantDataSource dataSource = new DataSourcePerTenantDataSource(tenantId -> {
		var url = registry.get(tenantId);
		IO.println("given tenant " + tenantId + ", the url is " + url);
		return DataSourceBuilder.create()
			.url(url)
			.username("myuser")
			.password("secret")
			.type(HikariDataSource.class)
			.build();
	});

	DockerComposeTenantAwareDataSourceSupplier(DockerHttpClient dockerHttpClient,
			DockerClientConfig dockerClientConfig) {
		this.dockerHttpClient = dockerHttpClient;
		this.dockerClientConfig = dockerClientConfig;
	}

	@Override
	public DataSource apply(String tenantId) {
		return this.dataSource;
	}

	private void refreshRegistry() {
		try (var dockerClient = DockerClientImpl.getInstance(this.dockerClientConfig, this.dockerHttpClient)) {
			var composeContainers = dockerClient.listContainersCmd()
				.withShowAll(false) // Only running containers
				.withLabelFilter(Collections.singletonList("com.docker.compose.service"))
				.exec();
			for (var container : composeContainers) {
				var serviceName = container.getLabels().get("com.docker.compose.service");
				var ports = container.getPorts();
				if (ports != null) {
					for (var port : ports) {
						this.registry.put(serviceName,
								"jdbc:postgresql://" + "localhost:" + port.getPublicPort() + "/" + serviceName);
					}
				}
			}
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		refreshRegistry();
	}

}
