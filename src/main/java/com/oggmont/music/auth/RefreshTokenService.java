package com.oggmont.music.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens (OAuth 2.0 Security BCP):
 * <ul>
 * <li>each refresh returns a new token and retires the old one;</li>
 * <li>every token from one login belongs to a family, and presenting a retired token
 * again (a sign it was stolen) revokes the whole family;</li>
 * <li>only a SHA-256 hash of each token is stored.</li>
 * </ul>
 */
@Service
class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository tokens;

	private final JwtProperties properties;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties, Clock clock) {
		this.tokens = tokens;
		this.properties = properties;
		this.clock = clock;
	}

	/** Issues the first token of a new family (a new login). */
	IssuedToken issue(int userId) {
		return issue(userId, UUID.randomUUID());
	}

	/**
	 * Exchanges a refresh token for a new one of the same family. Returns empty when the
	 * token is unknown, expired, revoked or reused (in which case its family is revoked).
	 * The outcome is returned rather than thrown so that a revocation is committed.
	 */
	@Transactional
	Optional<Rotated> rotate(String presented) {
		Optional<RefreshTokenRepository.StoredToken> found = this.tokens.findForUpdate(hash(presented));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		RefreshTokenRepository.StoredToken token = found.get();
		if (token.revokedAt() != null || token.usedAt() != null) {
			int revoked = this.tokens.revokeFamily(token.familyId());
			log.warn("Refresh token reuse for user {}: revoked {} token(s) of its family", token.userId(), revoked);
			return Optional.empty();
		}
		if (!token.expiresAt().isAfter(this.clock.instant())) {
			return Optional.empty();
		}
		this.tokens.markUsed(token.tokenId());
		return Optional.of(new Rotated(token.userId(), issue(token.userId(), token.familyId())));
	}

	/** Revokes the family of a token (logout). Unknown tokens are ignored. */
	@Transactional
	void revokeFamily(String presented) {
		this.tokens.findFamily(hash(presented)).ifPresent(this.tokens::revokeFamily);
	}

	/** Revokes every session of a user (password change). */
	void revokeAll(int userId) {
		this.tokens.revokeAllForUser(userId);
	}

	private IssuedToken issue(int userId, UUID familyId) {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = this.clock.instant();
		Instant expiresAt = now.plus(this.properties.refreshTokenTtl());
		this.tokens.insert(familyId, userId, hash(value), now, expiresAt);
		return new IssuedToken(value, expiresAt);
	}

	static byte[] hash(String token) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	record IssuedToken(String value, Instant expiresAt) {
	}

	record Rotated(int userId, IssuedToken token) {
	}

}
