package com.example.service;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * the idea is: this discovers the main dataSource, and the {@link TenantAwareDataSourceSupplier}, and combines them in
 * an {@link AdminDataSourceRoutingDataSource}. it will check the presence of a tenant and if its there, route to the underlying
 * tenant datasource (obtained by calling the supplier), <em>or</em> the main datasource.
 */
class AdminDataSourceRoutingDataSourceBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private final AtomicReference<ObjectProvider<TenantAwareDataSourceSupplier>> supplier = new AtomicReference<>();

    @Override
    public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource main) {
            Assert.notNull(this.supplier.get(), "No data source supplier found");
            var tenantAwareDataSourceSupplier = (TenantAwareDataSourceSupplier)
                    tenantId -> Objects.requireNonNull(supplier.get().getIfAvailable()).apply(tenantId);
            return new AdminDataSourceRoutingDataSource(main, tenantAwareDataSourceSupplier);
        }
        return bean;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        IO.println("AdminDataSourceRoutingDataSourceBeanPostProcessor setBeanFactory");
        this.supplier.set(beanFactory.getBeanProvider(TenantAwareDataSourceSupplier.class));
    }
}
