package com.example.service.schema;

import com.example.service.TenantAwareDataSourceSupplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
class DataSourceConfiguration {

	@Bean
	TenantAwareDataSourceSupplier tenantAwareDataSourceSupplier(ApplicationContext applicationContext) {
		return _ -> applicationContext.getBean(DataSource.class);
	}

	@Bean
	static SchemaPerTenantDataSourceBeanPostProcessor schemaPerTenantDataSourceBeanPostProcessor() {
		return new SchemaPerTenantDataSourceBeanPostProcessor();
	}

	static class SchemaPerTenantDataSourceBeanPostProcessor implements BeanPostProcessor {

		@Override
		public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof DataSource dataSource)
				return new SchemaPerTenantDataSource(dataSource);
			return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
		}

	}

}
