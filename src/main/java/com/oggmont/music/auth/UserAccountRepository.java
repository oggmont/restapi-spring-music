package com.oggmont.music.auth;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accounts as seen by authentication: {@code public.app_user} plus the API-owned
 * {@code auth.user_account} (password hash and role). A user without a
 * {@code user_account} row is a listener who can't log in with a password.
 */
@Repository
public class UserAccountRepository {

	private static final String LOGIN_COLUMNS = """
			SELECT u.user_id, u.username, u.auth_subject, a.password_hash, coalesce(a.role, 'listener') AS role
			FROM app_user u
			LEFT JOIN auth.user_account a ON a.user_id = u.user_id
			""";

	private static final RowMapper<LoginAccount> LOGIN_ACCOUNT = (rs, rowNum) -> new LoginAccount(rs.getInt("user_id"),
			rs.getString("username"), rs.getString("auth_subject"), rs.getString("password_hash"),
			Role.fromCode(rs.getString("role")));

	private final JdbcClient jdbc;

	UserAccountRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Caller> findCallerBySubject(String subject) {
		return this.jdbc.sql("""
				SELECT u.user_id, u.username, coalesce(a.role, 'listener') AS role
				FROM app_user u
				LEFT JOIN auth.user_account a ON a.user_id = u.user_id
				WHERE u.auth_subject = :subject
				""")
			.param("subject", subject)
			.query((rs, rowNum) -> new Caller(rs.getInt("user_id"), rs.getString("username"),
					Role.fromCode(rs.getString("role"))))
			.optional();
	}

	/** Looks an account up by username, or by email (case-insensitive) when the login contains '@'. */
	public Optional<LoginAccount> findLogin(String login) {
		String where = login.contains("@") ? "WHERE lower(u.email) = lower(:login)" : "WHERE u.username = :login";
		return this.jdbc.sql(LOGIN_COLUMNS + where).param("login", login).query(LOGIN_ACCOUNT).optional();
	}

	public Optional<LoginAccount> findLogin(int userId) {
		return this.jdbc.sql(LOGIN_COLUMNS + "WHERE u.user_id = :userId")
			.param("userId", userId)
			.query(LOGIN_ACCOUNT)
			.optional();
	}

	public Optional<Integer> findUserId(String username) {
		return this.jdbc.sql("SELECT user_id FROM app_user WHERE username = :username")
			.param("username", username)
			.query(Integer.class)
			.optional();
	}

	/**
	 * Creates the {@code app_user} row of a new account; empty if the username or email
	 * is already taken (lost a race with another insert).
	 */
	public Optional<Integer> insertUser(String username, String displayName, String email, @Nullable String countryCode,
			boolean verified) {
		return this.jdbc.sql("""
				INSERT INTO app_user (username, display_name, email, auth_subject, country_code, is_verified)
				VALUES (:username, :displayName, :email, :authSubject, :countryCode, :verified)
				ON CONFLICT DO NOTHING
				RETURNING user_id
				""")
			.param("username", username)
			.param("displayName", displayName)
			.param("email", email)
			.param("authSubject", newSubject())
			.param("countryCode", countryCode)
			.param("verified", verified)
			.query(Integer.class)
			.optional();
	}

	public void insertAccount(int userId, Role role, @Nullable String passwordHash) {
		this.jdbc.sql("""
				INSERT INTO auth.user_account (user_id, role, password_hash, password_changed_at)
				VALUES (:userId, :role, :passwordHash, CASE WHEN CAST(:passwordHash AS text) IS NOT NULL THEN now() END)
				""")
			.param("userId", userId)
			.param("role", role.code())
			.param("passwordHash", passwordHash)
			.update();
	}

	/** Sets (or replaces) the password, creating the {@code user_account} row when missing. */
	public void upsertPassword(int userId, String passwordHash) {
		this.jdbc.sql("""
				INSERT INTO auth.user_account (user_id, password_hash, password_changed_at)
				VALUES (:userId, :passwordHash, now())
				ON CONFLICT (user_id) DO UPDATE
				SET password_hash = EXCLUDED.password_hash, password_changed_at = now(), updated_at = now()
				""")
			.param("userId", userId)
			.param("passwordHash", passwordHash)
			.update();
	}

	/** Sets the role, creating the {@code user_account} row when missing. */
	public void upsertRole(int userId, Role role) {
		this.jdbc.sql("""
				INSERT INTO auth.user_account (user_id, role)
				VALUES (:userId, :role)
				ON CONFLICT (user_id) DO UPDATE SET role = EXCLUDED.role, updated_at = now()
				WHERE user_account.role IS DISTINCT FROM EXCLUDED.role
				""")
			.param("userId", userId)
			.param("role", role.code())
			.update();
	}

	/**
	 * Makes the account an admin. The password is only set when the account has none, so
	 * a password changed through the API survives restarts.
	 */
	public void grantAdmin(int userId, String passwordHashIfMissing) {
		this.jdbc.sql("""
				INSERT INTO auth.user_account (user_id, role, password_hash, password_changed_at)
				VALUES (:userId, 'admin', :passwordHash, now())
				ON CONFLICT (user_id) DO UPDATE
				SET role = 'admin',
				    password_hash = coalesce(user_account.password_hash, EXCLUDED.password_hash),
				    password_changed_at = coalesce(user_account.password_changed_at, EXCLUDED.password_changed_at),
				    updated_at = now()
				""")
			.param("userId", userId)
			.param("passwordHash", passwordHashIfMissing)
			.update();
	}

	/**
	 * Returns the account's {@code auth_subject} (the {@code sub} of its tokens), creating
	 * one for accounts that have none yet, such as the seed users.
	 */
	public String ensureSubject(int userId) {
		this.jdbc.sql("UPDATE app_user SET auth_subject = :subject WHERE user_id = :userId AND auth_subject IS NULL")
			.param("subject", newSubject())
			.param("userId", userId)
			.update();
		return this.jdbc.sql("SELECT auth_subject FROM app_user WHERE user_id = :userId")
			.param("userId", userId)
			.query(String.class)
			.single();
	}

	public void updatePasswordHash(int userId, String passwordHash) {
		this.jdbc.sql("UPDATE auth.user_account SET password_hash = :passwordHash, updated_at = now() WHERE user_id = :userId")
			.param("passwordHash", passwordHash)
			.param("userId", userId)
			.update();
	}

	private static String newSubject() {
		return UUID.randomUUID().toString();
	}

	/**
	 * @param userId {@code app_user.user_id}
	 * @param username {@code app_user.username}
	 * @param authSubject the {@code sub} of the account's tokens (null for seed users until
	 * they get a password)
	 * @param passwordHash the encoded password, or null when the account can't log in
	 * @param role the account's role
	 */
	public record LoginAccount(int userId, String username, @Nullable String authSubject, @Nullable String passwordHash,
			Role role) {
	}

}
