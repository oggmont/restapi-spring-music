package com.oggmont.music.auth;

import com.oggmont.music.common.jdbc.Rows;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code auth.refresh_token}: issued refresh tokens, identified by the SHA-256 hash of
 * their value.
 */
@Repository
class RefreshTokenRepository {

	private final JdbcClient jdbc;

	RefreshTokenRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void insert(UUID familyId, int userId, byte[] tokenHash, Instant issuedAt, Instant expiresAt) {
		this.jdbc.sql("""
				INSERT INTO auth.refresh_token (family_id, user_id, token_hash, issued_at, expires_at)
				VALUES (:familyId, :userId, :tokenHash, :issuedAt, :expiresAt)
				""")
			.param("familyId", familyId)
			.param("userId", userId)
			.param("tokenHash", tokenHash)
			.param("issuedAt", Rows.param(issuedAt))
			.param("expiresAt", Rows.param(expiresAt))
			.update();
	}

	/** Finds a token and locks it, so two concurrent refreshes can't both use it. */
	Optional<StoredToken> findForUpdate(byte[] tokenHash) {
		return this.jdbc.sql("""
				SELECT token_id, family_id, user_id, expires_at, used_at, revoked_at
				FROM auth.refresh_token
				WHERE token_hash = :tokenHash
				FOR UPDATE
				""")
			.param("tokenHash", tokenHash)
			.query((rs, rowNum) -> new StoredToken(rs.getObject("token_id", UUID.class),
					rs.getObject("family_id", UUID.class), rs.getInt("user_id"), Rows.requiredInstant(rs, "expires_at"),
					Rows.instant(rs, "used_at"), Rows.instant(rs, "revoked_at")))
			.optional();
	}

	Optional<UUID> findFamily(byte[] tokenHash) {
		return this.jdbc.sql("SELECT family_id FROM auth.refresh_token WHERE token_hash = :tokenHash")
			.param("tokenHash", tokenHash)
			.query(UUID.class)
			.optional();
	}

	void markUsed(UUID tokenId) {
		this.jdbc.sql("UPDATE auth.refresh_token SET used_at = now() WHERE token_id = :tokenId")
			.param("tokenId", tokenId)
			.update();
	}

	int revokeFamily(UUID familyId) {
		return this.jdbc.sql("""
				UPDATE auth.refresh_token SET revoked_at = now()
				WHERE family_id = :familyId AND revoked_at IS NULL
				""")
			.param("familyId", familyId)
			.update();
	}

	int revokeAllForUser(int userId) {
		return this.jdbc.sql("UPDATE auth.refresh_token SET revoked_at = now() WHERE user_id = :userId AND revoked_at IS NULL")
			.param("userId", userId)
			.update();
	}

	int deleteExpiredBefore(Instant cutoff) {
		return this.jdbc.sql("DELETE FROM auth.refresh_token WHERE expires_at < :cutoff")
			.param("cutoff", Rows.param(cutoff))
			.update();
	}

	record StoredToken(UUID tokenId, UUID familyId, int userId, Instant expiresAt, @Nullable Instant usedAt,
			@Nullable Instant revokedAt) {
	}

}
