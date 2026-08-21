package com.example.service.db;

import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class DockerDatabaseRegistry implements DatabaseRegistry, InitializingBean {

	private final Map<String, String> registry = new ConcurrentHashMap<>();

	private final DockerHttpClient dockerHttpClient;

	private final DockerClientConfig dockerClientConfig;

	DockerDatabaseRegistry(DockerHttpClient dockerHttpClient, DockerClientConfig dockerClientConfig) {
		this.dockerHttpClient = dockerHttpClient;
		this.dockerClientConfig = dockerClientConfig;
	}

	@Override
	public JdbcConnectionDetails get(String tenantId) {
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
				return registry.get(tenantId);
			}
		};
	}

	@Override
	public void afterPropertiesSet() throws Exception {
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
	}

}
