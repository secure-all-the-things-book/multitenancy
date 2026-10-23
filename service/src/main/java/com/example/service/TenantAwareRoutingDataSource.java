package com.example.service;

import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

import javax.sql.DataSource;
import java.util.function.Supplier;

class TenantAwareRoutingDataSource extends DelegatingDataSource {

	private final Supplier<DataSource> primary;

	// <.>
	TenantAwareRoutingDataSource(DataSource multitenant, Supplier<DataSource> primary) {
		super(multitenant);
		this.primary = SingletonSupplier.of(primary);
	}

	// <.>
	@Override
	protected DataSource obtainTargetDataSource() {
		var tenantId = TenantContext.getTenantIdentifier();
		return StringUtils.hasText(tenantId) ? super.obtainTargetDataSource() : this.primary.get();
	}

}
