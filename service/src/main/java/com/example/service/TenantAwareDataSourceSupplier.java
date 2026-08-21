package com.example.service;

import javax.sql.DataSource;
import java.util.function.Function;

public interface TenantAwareDataSourceSupplier extends Function<String, DataSource> {

}
