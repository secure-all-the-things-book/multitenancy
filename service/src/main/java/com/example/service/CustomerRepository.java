package com.example.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Repository
@Transactional
class CustomerRepository {

	private final JdbcClient jdbcClient;

	CustomerRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Collection<Customer> findAll() {
		return this.jdbcClient //
			.sql(" select * from customer ")//
			.query((rs, _) -> new Customer(rs.getInt("id"), rs.getString("name")))//
			.list();
	}

}
