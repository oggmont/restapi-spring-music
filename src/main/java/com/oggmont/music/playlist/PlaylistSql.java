package com.oggmont.music.playlist;

import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.user.UserRef;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Column list and row mapping of {@link PlaylistSummary}, from {@code playlist_summary ps}.
 */
public final class PlaylistSql {

	public static final String SUMMARY_COLUMNS = """
			ps.playlist_id, ps.name, ps.description, ps.visibility, ps.cover_url, ps.version, ps.follower_count,
			ps.owner_id, ps.owner_username, ps.owner_name, ps.is_collaborative, ps.collaborator_count,
			ps.track_count, ps.total_milliseconds, ps.created_at, ps.updated_at""";

	private PlaylistSql() {
	}

	public static PlaylistSummary summary(ResultSet rs) throws SQLException {
		return new PlaylistSummary(rs.getInt("playlist_id"), rs.getString("name"), rs.getString("description"),
				rs.getString("visibility"), rs.getString("cover_url"), owner(rs), rs.getBoolean("is_collaborative"),
				rs.getInt("track_count"), rs.getLong("total_milliseconds"), rs.getInt("follower_count"),
				rs.getInt("version"), Rows.requiredInstant(rs, "created_at"), Rows.requiredInstant(rs, "updated_at"));
	}

	static UserRef owner(ResultSet rs) throws SQLException {
		return new UserRef(rs.getInt("owner_id"), rs.getString("owner_username"), rs.getString("owner_name"));
	}

}
