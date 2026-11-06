package com.oggmont.music.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the tokens the API issues ({@code app.jwt.*}).
 *
 * @param secret Base64-encoded HMAC-SHA256 key of at least 256 bits; blank means a
 * random key per start (tokens stop working on restart)
 * @param issuer the {@code iss} claim, checked on every request
 * @param audience the {@code aud} claim, checked on every request
 * @param accessTokenTtl lifetime of access tokens
 * @param refreshTokenTtl lifetime of refresh tokens (renewed on every refresh)
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@Nullable String secret, @NotBlank @DefaultValue("api-spring-music") String issuer,
		@NotBlank @DefaultValue("api-spring-music") String audience,
		@NotNull @DefaultValue("15m") Duration accessTokenTtl, @NotNull @DefaultValue("14d") Duration refreshTokenTtl) {
}
