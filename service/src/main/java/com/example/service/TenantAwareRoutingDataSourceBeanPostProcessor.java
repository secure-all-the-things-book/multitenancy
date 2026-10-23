package com.example.service;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.util.function.Supplier;

class TenantAwareRoutingDataSourceBeanPostProcessor implements BeanPostProcessor {

	private final Supplier<DataSource> primary;

	TenantAwareRoutingDataSourceBeanPostProcessor(Supplier<DataSource> primary) {
		this.primary = primary;
	}

	@Override
	public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		// <.>
		if (bean instanceof DataSource multitenant) {
			return new TenantAwareRoutingDataSource(multitenant, this.primary);
		}
		return bean;
	}

}
