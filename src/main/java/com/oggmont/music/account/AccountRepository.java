package com.oggmont.music.account;

import com.oggmont.music.common.jdbc.ColumnChanges;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Writes to the caller's own {@code app_user} row.
 */
@Repository
class AccountRepository {

	private final JdbcClient jdbc;

	AccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Creates an account. A taken username or email fails with a unique violation, which
	 * the API reports as {@code 409 username_taken} / {@code 409 email_taken}.
	 */
	int insert(String username, String displayName, String email, @Nullable String countryCode) {
		return this.jdbc.sql("""
				INSERT INTO app_user (username, display_name, email, auth_subject, country_code)
				VALUES (:username, :displayName, :email, :authSubject, :countryCode)
				RETURNING user_id
				""")
			.param("username", username)
			.param("displayName", displayName)
			.param("email", email)
			.param("authSubject", UUID.randomUUID().toString())
			.param("countryCode", countryCode, Types.CHAR)
			.query(Integer.class)
			.single();
	}

	/** Applies the given column changes; {@code updated_at} is set by a trigger. */
	void update(int userId, ColumnChanges changes) {
		if (changes.isEmpty()) {
			return;
		}
		changes.bind(this.jdbc.sql("UPDATE app_user SET " + changes.setClause() + " WHERE user_id = :userId"))
			.param("userId", userId)
			.update();
	}

	boolean delete(int userId) {
		return this.jdbc.sql("DELETE FROM app_user WHERE user_id = :userId").param("userId", userId).update() > 0;
	}

	Optional<String> findUsername(int userId) {
		return this.jdbc.sql("SELECT username FROM app_user WHERE user_id = :userId")
			.param("userId", userId)
			.query(String.class)
			.optional();
	}

}
