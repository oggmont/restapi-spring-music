package com.oggmont.music.playlist;

import com.oggmont.music.common.jdbc.Rows;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code playlist_collaborator}. The owner is never listed (a trigger rejects it).
 */
@Repository
class CollaboratorRepository {

	private static final String SELECT = """
			SELECT u.user_id, u.username, u.display_name, u.avatar_url, c.role, c.added_at
			FROM playlist_collaborator c
			JOIN app_user u ON u.user_id = c.user_id
			""";

	private static final RowMapper<Collaborator> COLLABORATOR = (rs, rowNum) -> new Collaborator(rs.getInt("user_id"),
			rs.getString("username"), rs.getString("display_name"), rs.getString("avatar_url"), rs.getString("role"),
			Rows.requiredInstant(rs, "added_at"));

	private final JdbcClient jdbc;

	CollaboratorRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Collaborator> list(int playlistId) {
		return this.jdbc.sql(SELECT + "WHERE c.playlist_id = :playlistId ORDER BY c.added_at, u.username")
			.param("playlistId", playlistId)
			.query(COLLABORATOR)
			.list();
	}

	/** Adds a collaborator or changes their role; returns true if they were added. */
	boolean upsert(int playlistId, int userId, String role) {
		return this.jdbc.sql("""
				INSERT INTO playlist_collaborator (playlist_id, user_id, role)
				VALUES (:playlistId, :userId, :role)
				ON CONFLICT (playlist_id, user_id) DO UPDATE SET role = EXCLUDED.role
				RETURNING (xmax = 0) AS inserted
				""")
			.param("playlistId", playlistId)
			.param("userId", userId)
			.param("role", role)
			.query(Boolean.class)
			.single();
	}

	Collaborator find(int playlistId, int userId) {
		return this.jdbc.sql(SELECT + "WHERE c.playlist_id = :playlistId AND c.user_id = :userId")
			.param("playlistId", playlistId)
			.param("userId", userId)
			.query(COLLABORATOR)
			.single();
	}

	void remove(int playlistId, int userId) {
		this.jdbc.sql("DELETE FROM playlist_collaborator WHERE playlist_id = :playlistId AND user_id = :userId")
			.param("playlistId", playlistId)
			.param("userId", userId)
			.update();
	}

}
