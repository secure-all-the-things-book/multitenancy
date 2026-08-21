package com.example.service.db;

import io.arconia.multitenancy.core.context.TenantContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

class DatabasePerTenantDataSource extends DelegatingDataSource {

	DatabasePerTenantDataSource(Function<String, DataSource> supplier) {
		var tenants = new ConcurrentHashMap<String, DataSource>();
		var pfb = new ProxyFactoryBean();
		pfb.addInterface(XADataSource.class);
		pfb.addInterface(DataSource.class);
		pfb.addAdvice((MethodInterceptor) invocation -> {
			var tenantIdentifier = TenantContext.getTenantIdentifier();
			var db = tenants.computeIfAbsent(tenantIdentifier, supplier);
			return invocation.getMethod().invoke(db, invocation.getArguments());
		});
		var db = (DataSource) pfb.getObject();
		this.setTargetDataSource(db);
	}

}
