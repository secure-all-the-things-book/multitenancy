package com.example.service.schema;

import io.arconia.multitenancy.core.context.TenantContext;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

import javax.sql.DataSource;
import java.util.function.Supplier;

class TenantAwareRoutingDataSource extends DelegatingDataSource {

	private final Supplier<DataSource> multitenant;

	// <.>
	TenantAwareRoutingDataSource(DataSource primary, Supplier<DataSource> multitenant) {
		super(primary);
		this.multitenant = SingletonSupplier.of(multitenant);
	}

	// <.>
	@Override
	protected DataSource obtainTargetDataSource() {
		var tenantId = TenantContext.getTenantIdentifier();
		return StringUtils.hasText(tenantId) ? this.multitenant.get() : super.obtainTargetDataSource();
	}

}
