package com.oggmont.music.auth;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * An authenticated request: the {@link Caller} as principal, the verified access token
 * as credentials, and the caller's role as authority.
 */
public final class CallerAuthenticationToken extends AbstractAuthenticationToken {

	private static final long serialVersionUID = 1L;

	private final Caller caller;

	private final Jwt token;

	public CallerAuthenticationToken(Caller caller, Jwt token) {
		super(List.of(new SimpleGrantedAuthority(caller.role().authority())));
		this.caller = caller;
		this.token = token;
		setAuthenticated(true);
	}

	@Override
	public Caller getPrincipal() {
		return this.caller;
	}

	@Override
	public Jwt getCredentials() {
		return this.token;
	}

	@Override
	public String getName() {
		return this.caller.username();
	}

}
