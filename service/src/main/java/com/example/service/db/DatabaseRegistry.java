package com.example.service.db;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;

interface DatabaseRegistry {

	JdbcConnectionDetails getDatasourceConnectionDetails(String tenantId);

}
