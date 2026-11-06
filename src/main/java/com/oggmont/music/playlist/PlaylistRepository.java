package com.oggmont.music.playlist;

import com.oggmont.music.catalog.CatalogSql;
import com.oggmont.music.common.jdbc.ColumnChanges;
import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.user.UserRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Playlists: {@code playlist} and the {@code playlist_summary} view. Access checks use
 * the schema's {@code playlist_can_view()} and {@code playlist_can_edit()}.
 */
@Repository
class PlaylistRepository {

	private static final String SUMMARY_COLUMNS = PlaylistSql.SUMMARY_COLUMNS;

	private static final String DETAIL_SELECT = "SELECT " + SUMMARY_COLUMNS + """
			,
			       p.share_token,
			       playlist_can_edit(p.playlist_id, :me) AS can_edit,
			       EXISTS (SELECT 1 FROM playlist_follow f WHERE f.playlist_id = p.playlist_id AND f.user_id = :me) AS is_following
			FROM playlist p
			JOIN playlist_summary ps ON ps.playlist_id = p.playlist_id
			""";

	private final JdbcClient jdbc;

	PlaylistRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * What the caller may do with a playlist. With {@code lock}, the row is locked
	 * ({@code FOR UPDATE}) until the transaction ends, so the version can't change between
	 * the {@code If-Match} check and the write.
	 */
	Optional<PlaylistAccess> access(int playlistId, @Nullable Integer me, boolean lock) {
		return this.jdbc.sql("""
				SELECT p.owner_id, p.version, p.visibility,
				       playlist_can_view(p.playlist_id, :me) AS can_view,
				       playlist_can_edit(p.playlist_id, :me) AS can_edit,
				       EXISTS (SELECT 1 FROM playlist_collaborator c
				               WHERE c.playlist_id = p.playlist_id AND c.user_id = :me) AS is_collaborator
				FROM playlist p
				WHERE p.playlist_id = :playlistId
				""" + (lock ? "FOR UPDATE OF p" : ""))
			.param("me", me, Types.INTEGER)
			.param("playlistId", playlistId)
			.query((rs, rowNum) -> new PlaylistAccess(playlistId, rs.getInt("owner_id"), rs.getInt("version"),
					rs.getString("visibility"), rs.getBoolean("can_view"), rs.getBoolean("can_edit"),
					rs.getBoolean("is_collaborator"), me))
			.optional();
	}

	/** A playlist by share token, unless it is private (share links only work while unlisted or public). */
	Optional<Integer> findIdByShareToken(UUID shareToken) {
		return this.jdbc.sql("SELECT playlist_id FROM playlist WHERE share_token = :token AND visibility <> 'private'")
			.param("token", shareToken)
			.query(Integer.class)
			.optional();
	}

	/** The playlist's details, if the caller can view it. */
	Optional<PlaylistDetail> findDetail(int playlistId, @Nullable Integer me) {
		return this.jdbc
			.sql(DETAIL_SELECT + "WHERE p.playlist_id = :playlistId AND playlist_can_view(p.playlist_id, :me)")
			.param("me", me, Types.INTEGER)
			.param("playlistId", playlistId)
			.query((rs, rowNum) -> detail(rs, me, false))
			.optional();
	}

	/** The playlist behind a share link, without its share token. */
	Optional<PlaylistDetail> findDetailByShareToken(UUID shareToken, @Nullable Integer me) {
		return this.jdbc.sql(DETAIL_SELECT + "WHERE p.share_token = :token AND p.visibility <> 'private'")
			.param("me", me, Types.INTEGER)
			.param("token", shareToken)
			.query((rs, rowNum) -> detail(rs, me, true))
			.optional();
	}

	int insert(int ownerId, String name, @Nullable String description, @Nullable String visibility,
			@Nullable String coverUrl) {
		return this.jdbc.sql("""
				INSERT INTO playlist (name, description, owner_id, visibility, cover_url)
				VALUES (:name, :description, :ownerId, coalesce(:visibility, 'public'), :coverUrl)
				RETURNING playlist_id
				""")
			.param("name", name)
			.param("description", description, Types.VARCHAR)
			.param("ownerId", ownerId)
			.param("visibility", visibility, Types.VARCHAR)
			.param("coverUrl", coverUrl, Types.VARCHAR)
			.query(Integer.class)
			.single();
	}

	/** Copies a playlist's details into a new private playlist owned by {@code ownerId}. */
	int copy(int sourceId, int ownerId, @Nullable String name) {
		return this.jdbc.sql("""
				INSERT INTO playlist (name, description, owner_id, visibility, cover_url)
				SELECT coalesce(:name, name), description, :ownerId, 'private', cover_url
				FROM playlist
				WHERE playlist_id = :sourceId
				RETURNING playlist_id
				""")
			.param("name", name, Types.VARCHAR)
			.param("ownerId", ownerId)
			.param("sourceId", sourceId)
			.query(Integer.class)
			.single();
	}

	/** Applies the given changes (column names come from a fixed list in the service). */
	void update(int playlistId, ColumnChanges changes) {
		if (changes.isEmpty()) {
			return;
		}
		changes.bind(this.jdbc.sql("UPDATE playlist SET " + changes.setClause() + " WHERE playlist_id = :playlistId"))
			.param("playlistId", playlistId)
			.update();
	}

	void delete(int playlistId) {
		this.jdbc.sql("DELETE FROM playlist WHERE playlist_id = :playlistId").param("playlistId", playlistId).update();
	}

	UUID regenerateShareToken(int playlistId) {
		return this.jdbc.sql("""
				UPDATE playlist SET share_token = gen_random_uuid()
				WHERE playlist_id = :playlistId
				RETURNING share_token
				""")
			.param("playlistId", playlistId)
			.query(UUID.class)
			.single();
	}

	/** Playlists the user owns or collaborates on, most recently changed first, with the user's role. */
	Page<PlaylistSummary> findForMember(int me, PageParams page) {
		List<PlaylistSummary> items = this.jdbc.sql("""
				WITH mine AS (
				    SELECT playlist_id, 'owner' AS my_role FROM playlist WHERE owner_id = :me
				    UNION ALL
				    SELECT playlist_id, role FROM playlist_collaborator WHERE user_id = :me
				), page AS (
				    SELECT m.playlist_id, m.my_role, p.updated_at
				    FROM mine m
				    JOIN playlist p ON p.playlist_id = m.playlist_id
				    ORDER BY p.updated_at DESC, m.playlist_id DESC
				    LIMIT :limit OFFSET :offset
				)
				SELECT
				""" + SUMMARY_COLUMNS + """
				, page.my_role
				FROM page
				JOIN playlist_summary ps ON ps.playlist_id = page.playlist_id
				ORDER BY page.updated_at DESC, page.playlist_id DESC
				""")
			.param("me", me)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> PlaylistSql.summary(rs, rs.getString("my_role")))
			.list();
		long total = this.jdbc.sql("""
				SELECT (SELECT count(*) FROM playlist WHERE owner_id = :me)
				     + (SELECT count(*) FROM playlist_collaborator WHERE user_id = :me)
				""").param("me", me).query(Long.class).single();
		return Page.of(items, page, total);
	}

	/** A user's public playlists, oldest first (the order shown on their profile). */
	Page<PlaylistSummary> findPublicByOwner(int ownerId, PageParams page) {
		List<PlaylistSummary> items = this.jdbc.sql("""
				WITH page AS (
				    SELECT playlist_id, created_at FROM playlist
				    WHERE owner_id = :ownerId AND visibility = 'public'
				    ORDER BY created_at, playlist_id
				    LIMIT :limit OFFSET :offset
				)
				SELECT
				""" + SUMMARY_COLUMNS + """

				FROM page
				JOIN playlist_summary ps ON ps.playlist_id = page.playlist_id
				ORDER BY page.created_at, page.playlist_id
				""")
			.param("ownerId", ownerId)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> PlaylistSql.summary(rs, null))
			.list();
		long total = this.jdbc
			.sql("SELECT count(*) FROM playlist WHERE owner_id = :ownerId AND visibility = 'public'")
			.param("ownerId", ownerId)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	/**
	 * The newest entries in playlists the user follows or collaborates on (example 23 in
	 * examples.sql), skipping playlists the user can no longer view.
	 */
	Page<FeedItem> feed(int me, PageParams page) {
		String visible = """
				WITH feed_playlists AS (
				    SELECT playlist_id FROM playlist_follow WHERE user_id = :me
				    UNION
				    SELECT playlist_id FROM playlist_collaborator WHERE user_id = :me
				), visible AS (
				    SELECT playlist_id FROM feed_playlists WHERE playlist_can_view(playlist_id, :me)
				)
				""";
		List<FeedItem> items = this.jdbc.sql(visible + """
				, page AS (
				    SELECT pt.playlist_id, pt.position, pt.track_id, pt.added_by, pt.added_at
				    FROM playlist_track pt
				    WHERE pt.playlist_id IN (SELECT playlist_id FROM visible)
				    ORDER BY pt.added_at DESC, pt.playlist_id, pt.position
				    LIMIT :limit OFFSET :offset
				)
				SELECT page.playlist_id, p.name AS playlist_name, page.position, page.added_at,
				       u.user_id AS added_by_id, u.username AS added_by_username, u.display_name AS added_by_name,
				       """ + CatalogSql.TRACK_CARD_COLUMNS + ",\n" + CatalogSql.IS_LIKED + """

				FROM page
				JOIN playlist p ON p.playlist_id = page.playlist_id
				JOIN track_detail td ON td.track_id = page.track_id
				LEFT JOIN app_user u ON u.user_id = page.added_by
				ORDER BY page.added_at DESC, page.playlist_id, page.position
				""")
			.param("me", me, Types.INTEGER)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new FeedItem(
					new FeedItem.PlaylistRef(rs.getInt("playlist_id"), rs.getString("playlist_name")),
					rs.getInt("position"), Rows.requiredInstant(rs, "added_at"), addedBy(rs),
					CatalogSql.trackCard(rs, true)))
			.list();
		long total = this.jdbc
			.sql(visible + "SELECT count(*) FROM playlist_track WHERE playlist_id IN (SELECT playlist_id FROM visible)")
			.param("me", me, Types.INTEGER)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	static @Nullable UserRef addedBy(ResultSet rs) throws SQLException {
		Integer userId = Rows.integer(rs, "added_by_id");
		return (userId != null)
				? new UserRef(userId, rs.getString("added_by_username"), rs.getString("added_by_name")) : null;
	}

	private static PlaylistDetail detail(ResultSet rs, @Nullable Integer me, boolean viaShareLink) throws SQLException {
		UserRef owner = PlaylistSql.owner(rs);
		boolean isOwner = me != null && me == owner.userId();
		UUID shareToken = (isOwner && !viaShareLink) ? rs.getObject("share_token", UUID.class) : null;
		PlaylistDetail.MyAccess myAccess = (me != null)
				? new PlaylistDetail.MyAccess(true, rs.getBoolean("can_edit"), rs.getBoolean("is_following")) : null;
		return new PlaylistDetail(rs.getInt("playlist_id"), rs.getString("name"), rs.getString("description"),
				rs.getString("visibility"), shareToken, rs.getString("cover_url"), owner,
				rs.getBoolean("is_collaborative"), rs.getInt("collaborator_count"), rs.getInt("track_count"),
				rs.getLong("total_milliseconds"), rs.getInt("follower_count"), rs.getInt("version"), myAccess,
				Rows.requiredInstant(rs, "created_at"), Rows.requiredInstant(rs, "updated_at"));
	}

	/**
	 * Access facts about one playlist for one caller.
	 *
	 * @param playlistId the playlist
	 * @param ownerId its owner
	 * @param version its current version
	 * @param visibility public, unlisted or private
	 * @param canView {@code playlist_can_view(playlist, caller)}
	 * @param canEdit {@code playlist_can_edit(playlist, caller)}
	 * @param isCollaborator whether the caller is an editor or viewer
	 * @param me the caller, or null when anonymous
	 */
	record PlaylistAccess(int playlistId, int ownerId, int version, String visibility, boolean canView, boolean canEdit,
			boolean isCollaborator, @Nullable Integer me) {

		boolean isOwner() {
			return this.me != null && this.me == this.ownerId;
		}

	}

}
