package com.oggmont.music.auth;

/**
 * Tokens returned by login and refresh, in the shape of an OAuth 2.0 token response
 * (RFC 6749, section 5.1).
 *
 * @param accessToken JWT to send as {@code Authorization: Bearer <access_token>}
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the access token expires
 * @param refreshToken opaque token for {@code POST /auth/refresh}; single use
 * @param refreshTokenExpiresIn seconds until the refresh token expires
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
		long refreshTokenExpiresIn) {
}
