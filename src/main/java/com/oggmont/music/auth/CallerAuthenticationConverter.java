package com.oggmont.music.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a verified access token into a {@link CallerAuthenticationToken}: reads the
 * token's {@code sub} claim and looks up {@code app_user.auth_subject} (API-Design.md,
 * "Authentication"). The role comes from the database, so role changes apply at once,
 * and a token whose account was deleted stops working.
 */
@Component
class CallerAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserAccountRepository accounts;

	CallerAuthenticationConverter(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String subject = jwt.getSubject();
		if (subject == null || subject.isBlank()) {
			throw new InvalidBearerTokenException("The access token has no subject");
		}
		Caller caller = this.accounts.findCallerBySubject(subject)
			.orElseThrow(() -> new InvalidBearerTokenException("The access token's account no longer exists"));
		return new CallerAuthenticationToken(caller, jwt);
	}

}
