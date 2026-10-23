package com.example.service;

import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

// <.>
@Profile("db")
@Configuration
class TenantAwareRoutingDataSourceConfiguration {

	// <.>
	@Bean
	static TenantAwareRoutingDataSourceBeanPostProcessor tenantAwareRoutingDataSourceBeanPostProcessor(
			ObjectProvider<DataSourceProperties> properties) {
		return new TenantAwareRoutingDataSourceBeanPostProcessor(//
				() -> properties.getObject().initializeDataSourceBuilder().build());
	}

	// <.>
	@Bean
	ApplicationRunner tenantlessRunner(JdbcClient db) {
		return _ -> {
			IO.println("no tenant: " + currentDatabase(db));
			// <.>
			TenantContext.where("acmewidgets").run(() -> IO.println("acmewidgets: " + currentDatabase(db)));
		};
	}

	private static String currentDatabase(JdbcClient db) {
		return db.sql("select current_database()").query(String.class).single();
	}

}
