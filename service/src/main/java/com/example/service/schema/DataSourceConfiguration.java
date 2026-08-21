package com.example.service.schema;

import com.example.service.DataSourceInitializer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

@Profile("schema")
@Configuration
class DataSourceConfiguration {

	@Bean
	static SchemaPerTenantDataSourceBeanPostProcessor schemaPerTenantDataSourceBeanPostProcessor() {
		return new SchemaPerTenantDataSourceBeanPostProcessor();
	}

	static class SchemaPerTenantDataSourceBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

		private final AtomicReference<ObjectProvider<DataSourceInitializer>> dataSource = new AtomicReference<>();

		@Override
		public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof DataSource dataSource)
				return new SchemaPerTenantDataSource(dataSource, this.dataSource.get().getIfAvailable());
			return bean;
		}

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.dataSource.set(applicationContext.getBeanProvider(DataSourceInitializer.class));
		}

	}

}
