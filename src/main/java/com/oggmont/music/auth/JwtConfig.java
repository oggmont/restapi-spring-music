package com.oggmont.music.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT signing and verification with one HMAC-SHA256 key: the API issues its access
 * tokens and verifies them as an OAuth2 resource server. Verification checks the
 * signature, expiry ({@code exp}, {@code nbf}, 60 s clock skew), issuer and audience.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	private static final int MIN_KEY_BYTES = 32;

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		return new SecretKeySpec(keyBytes(properties.secret()), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
		OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				(aud) -> aud != null && aud.contains(properties.audience()));
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuer()), audience));
		return decoder;
	}

	private static byte[] keyBytes(@org.jspecify.annotations.Nullable String secret) {
		if (secret == null || secret.isBlank()) {
			log.warn("APP_JWT_SECRET is not set: signing tokens with a random key, so they stop working when the API "
					+ "restarts. Set APP_JWT_SECRET (e.g. `openssl rand -base64 48`).");
			byte[] random = new byte[MIN_KEY_BYTES];
			new SecureRandom().nextBytes(random);
			return random;
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(secret.trim());
		}
		catch (IllegalArgumentException ex) {
			try {
				bytes = Base64.getUrlDecoder().decode(secret.trim());
			}
			catch (IllegalArgumentException urlEx) {
				throw new IllegalStateException("APP_JWT_SECRET must be Base64-encoded (e.g. `openssl rand -base64 48`)",
						ex);
			}
		}
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException("APP_JWT_SECRET must decode to at least " + MIN_KEY_BYTES
					+ " bytes (256 bits) for HS256, but has " + bytes.length);
		}
		return bytes;
	}

}
