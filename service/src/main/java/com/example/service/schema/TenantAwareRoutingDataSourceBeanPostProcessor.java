package com.example.service.schema;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.util.function.Function;

class TenantAwareRoutingDataSourceBeanPostProcessor implements BeanPostProcessor {

	private final Function<DataSource, DataSource> multitenant;

	// <.>
	TenantAwareRoutingDataSourceBeanPostProcessor(Function<DataSource, DataSource> multitenant) {
		this.multitenant = multitenant;
	}

	@Override
	public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		// <.>
		if (bean instanceof DataSource primary) {
			return new TenantAwareRoutingDataSource(primary, () -> this.multitenant.apply(primary));
		}
		return bean;
	}

}
