package com.example.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
class TenantOAuth2TokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

	private final JdbcClient db;

	TenantOAuth2TokenCustomizer(JdbcClient db) {
		this.db = db;
	}

	@Override
	public void customize(JwtEncodingContext context) {
		// <.>
		var tenant = this.db //
			.sql(//
					" select tenant_details_identifier from users_tenant_details utd  " + //
							" where users_username = ? "//
			) //
			.params(context.getPrincipal().getName())
			.query((rs, _) -> rs.getString("tenant_details_identifier"))
			.single();
		// <.>
		context.getClaims().claim("tenant", tenant);
	}

}
