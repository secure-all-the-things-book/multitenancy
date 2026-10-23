package com.example.service.schema;

import com.example.service.DataSourceInitializer;
import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

// <.>
@Profile("routing")
@Configuration
class TenantAwareRoutingDataSourceConfiguration {

	// <.>
	@Bean
	static TenantAwareRoutingDataSourceBeanPostProcessor tenantAwareRoutingDataSourceBeanPostProcessor(
			ObjectProvider<DataSourceInitializer> dataSourceInitializer) {
		return new TenantAwareRoutingDataSourceBeanPostProcessor(//
				primary -> new SchemaPerTenantDataSource(primary, dataSourceInitializer.getObject()));
	}

	// <.>
	@Bean
	ApplicationRunner tenantlessRunner(JdbcClient db) {
		return _ -> {
			IO.println("no tenant: " + currentSchema(db));
			// <.>
			TenantContext.where("acmewidgets").run(() -> IO.println("acmewidgets: " + currentSchema(db)));
		};
	}

	private static String currentSchema(JdbcClient db) {
		return db.sql("select current_schema()").query(String.class).single();
	}

}
