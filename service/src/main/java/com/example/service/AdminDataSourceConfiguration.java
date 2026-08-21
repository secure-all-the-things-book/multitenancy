package com.example.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdminDataSourceConfiguration {

	@Bean
	static AdminDataSourceRoutingDataSourceBeanPostProcessor adminDataSourceRoutingDataSourceBeanPostProcessor() {
		return new AdminDataSourceRoutingDataSourceBeanPostProcessor();
	}

}
