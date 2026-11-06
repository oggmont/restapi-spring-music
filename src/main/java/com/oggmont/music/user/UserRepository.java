package com.oggmont.music.user;

import com.oggmont.music.auth.Role;
import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads of {@code app_user} and {@code user_follow}.
 */
@Repository
public class UserRepository {

	private static final String PROFILE_COLUMNS = """
			u.user_id, u.username, u.display_name, u.country_code, u.bio, u.avatar_url, u.is_verified,
			u.follower_count, u.following_count, u.created_at""";

	private static final RowMapper<UserCard> USER_CARD = (rs, rowNum) -> new UserCard(rs.getInt("user_id"),
			rs.getString("username"), rs.getString("display_name"), rs.getString("avatar_url"),
			rs.getBoolean("is_verified"), rs.getInt("follower_count"), Rows.instant(rs, "followed_at"));

	private final JdbcClient jdbc;

	UserRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<Integer> findUserId(String username) {
		return this.jdbc.sql("SELECT user_id FROM app_user WHERE username = :username")
			.param("username", username)
			.query(Integer.class)
			.optional();
	}

	/** A public profile; {@code is_followed_by_me} is set when {@code me} isn't null. */
	public Optional<UserProfile> findPublicProfile(String username, @Nullable Integer me) {
		return this.jdbc.sql("SELECT " + PROFILE_COLUMNS + """
				,
				       CASE WHEN CAST(:me AS integer) IS NULL THEN NULL
				            ELSE EXISTS (SELECT 1 FROM user_follow f WHERE f.follower_id = :me AND f.followed_id = u.user_id)
				       END AS is_followed_by_me
				FROM app_user u
				WHERE u.username = :username
				""")
			.param("me", me, Types.INTEGER)
			.param("username", username)
			.query((rs, rowNum) -> new UserProfile(rs.getInt("user_id"), rs.getString("username"),
					rs.getString("display_name"), rs.getString("country_code"), rs.getString("bio"),
					rs.getString("avatar_url"), rs.getBoolean("is_verified"), rs.getInt("follower_count"),
					rs.getInt("following_count"), Rows.requiredInstant(rs, "created_at"), null, null, null,
					Rows.bool(rs, "is_followed_by_me")))
			.optional();
	}

	/** The owner's view of a profile, with email, role and {@code updated_at}. */
	public Optional<UserProfile> findOwnProfile(int userId) {
		return this.jdbc.sql("SELECT " + PROFILE_COLUMNS + """
				, u.email, u.updated_at, coalesce(a.role, 'listener') AS role
				FROM app_user u
				LEFT JOIN auth.user_account a ON a.user_id = u.user_id
				WHERE u.user_id = :userId
				""")
			.param("userId", userId)
			.query((rs, rowNum) -> new UserProfile(rs.getInt("user_id"), rs.getString("username"),
					rs.getString("display_name"), rs.getString("country_code"), rs.getString("bio"),
					rs.getString("avatar_url"), rs.getBoolean("is_verified"), rs.getInt("follower_count"),
					rs.getInt("following_count"), Rows.requiredInstant(rs, "created_at"), rs.getString("email"),
					Role.fromCode(rs.getString("role")), Rows.instant(rs, "updated_at"), null))
			.optional();
	}

	/** Who follows the user, newest first. */
	public Page<UserCard> followers(int userId, PageParams page) {
		List<UserCard> items = this.jdbc.sql("""
				SELECT u.user_id, u.username, u.display_name, u.avatar_url, u.is_verified, u.follower_count, f.followed_at
				FROM user_follow f
				JOIN app_user u ON u.user_id = f.follower_id
				WHERE f.followed_id = :userId
				ORDER BY f.followed_at DESC, u.username
				LIMIT :limit OFFSET :offset
				""")
			.param("userId", userId)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query(USER_CARD)
			.list();
		long total = count("SELECT count(*) FROM user_follow WHERE followed_id = :userId", userId);
		return Page.of(items, page, total);
	}

	/** Users the user follows, newest first. */
	public Page<UserCard> following(int userId, PageParams page) {
		List<UserCard> items = this.jdbc.sql("""
				SELECT u.user_id, u.username, u.display_name, u.avatar_url, u.is_verified, u.follower_count, f.followed_at
				FROM user_follow f
				JOIN app_user u ON u.user_id = f.followed_id
				WHERE f.follower_id = :userId
				ORDER BY f.followed_at DESC, u.username
				LIMIT :limit OFFSET :offset
				""")
			.param("userId", userId)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query(USER_CARD)
			.list();
		long total = count("SELECT count(*) FROM user_follow WHERE follower_id = :userId", userId);
		return Page.of(items, page, total);
	}

	private long count(String sql, int userId) {
		return this.jdbc.sql(sql).param("userId", userId).query(Long.class).single();
	}

}
