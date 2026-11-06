package com.oggmont.music.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens (JWT, HS256).
 * <p>
 * Claims: {@code iss}, {@code aud}, {@code sub} (the account's {@code auth_subject}),
 * {@code iat}/{@code nbf}/{@code exp}, {@code jti}, plus {@code preferred_username} and
 * {@code role} for clients. Authorization never trusts the {@code role} claim: it is
 * read from the database on every request.
 */
@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AccessToken issueAccessToken(String subject, String username, Role role) {
		Instant now = this.clock.instant();
		Instant expiresAt = now.plus(this.properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.properties.issuer())
			.audience(List.of(this.properties.audience()))
			.subject(subject)
			.issuedAt(now)
			.notBefore(now)
			.expiresAt(expiresAt)
			.id(UUID.randomUUID().toString())
			.claim("preferred_username", username)
			.claim("role", role.code())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String value = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt);
	}

	/**
	 * @param value the encoded JWT
	 * @param expiresAt when it expires
	 */
	public record AccessToken(String value, Instant expiresAt) {
	}

}
