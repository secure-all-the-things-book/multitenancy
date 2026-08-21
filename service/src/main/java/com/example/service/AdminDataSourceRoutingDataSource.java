package com.example.service;

import io.arconia.multitenancy.core.context.TenantContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.function.Function;

class AdminDataSourceRoutingDataSource
        extends DelegatingDataSource {

    AdminDataSourceRoutingDataSource(
            DataSource main,
            Function<String, DataSource> tenantDataSource,
            DataSourceInitializer dataSourceInitializer
    ) {
        var pfb = new ProxyFactoryBean();
        pfb.addInterface(XADataSource.class);
        pfb.addInterface(DataSource.class);
        pfb.addAdvice((MethodInterceptor) invocation -> {
            var tenantIdentifier = TenantContext.getTenantIdentifier();
            var db = StringUtils.hasText(tenantIdentifier) ?
                    dataSourceInitializer.initialize(tenantIdentifier, tenantDataSource.apply(tenantIdentifier)) :
                    main;
            return invocation.getMethod().invoke(db, invocation.getArguments());
        });
        var targetDataSource = (DataSource) pfb.getObject();
        this.setTargetDataSource(targetDataSource);
    }

}

