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
				SELECT p.name, p.owner_id, p.version, p.visibility,
				       playlist_can_view(p.playlist_id, :me) AS can_view,
				       playlist_can_edit(p.playlist_id, :me) AS can_edit,
				       EXISTS (SELECT 1 FROM playlist_collaborator c
				               WHERE c.playlist_id = p.playlist_id AND c.user_id = :me) AS is_collaborator
				FROM playlist p
				WHERE p.playlist_id = :playlistId
				""" + (lock ? "FOR UPDATE OF p" : ""))
			.param("me", me, Types.INTEGER)
			.param("playlistId", playlistId)
			.query((rs, rowNum) -> new PlaylistAccess(playlistId, rs.getString("name"), rs.getInt("owner_id"),
					rs.getInt("version"),
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

	/**
	 * Creates a playlist. It is private: visibility follows favorites, and a trigger sets
	 * it. A 21st playlist fails with {@code playlist_owner_limit}.
	 */
	int insert(int ownerId, String name, @Nullable String description, @Nullable String coverUrl) {
		return this.jdbc.sql("""
				INSERT INTO playlist (name, description, owner_id, cover_url)
				VALUES (:name, :description, :ownerId, :coverUrl)
				RETURNING playlist_id
				""")
			.param("name", name)
			.param("description", description, Types.VARCHAR)
			.param("ownerId", ownerId)
			.param("coverUrl", coverUrl, Types.VARCHAR)
			.query(Integer.class)
			.single();
	}

	/** Copies a playlist's details into a new (private) playlist owned by {@code ownerId}. */
	int copy(int sourceId, int ownerId, @Nullable String name) {
		return this.jdbc.sql("""
				INSERT INTO playlist (name, description, owner_id, cover_url)
				SELECT coalesce(:name, name), description, :ownerId, cover_url
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

	/** How many playlists the user owns (the limit is 20). */
	int countOwned(int ownerId) {
		return this.jdbc.sql("SELECT count(*) FROM playlist WHERE owner_id = :ownerId")
			.param("ownerId", ownerId)
			.query(Integer.class)
			.single();
	}

	/**
	 * Playlists the user owns or collaborates on, with the user's role, whether it is a
	 * favorite, when the user last added to it and, with {@code containsTrack}, whether it
	 * holds that track.
	 *
	 * @param role only playlists where the user has this role (owner, editor, viewer), or all
	 * @param sort the order
	 */
	Page<MyPlaylist> findForMember(int me, @Nullable String role, MemberSort sort, @Nullable Integer containsTrack,
			PageParams page) {
		String mine = """
				WITH mine AS (
				    SELECT playlist_id, 'owner' AS my_role FROM playlist WHERE owner_id = :me
				    UNION ALL
				    SELECT playlist_id, role FROM playlist_collaborator WHERE user_id = :me
				), filtered AS (
				    SELECT playlist_id, my_role FROM mine WHERE CAST(:role AS text) IS NULL OR my_role = :role
				)
				""";
		List<MyPlaylist> items = this.jdbc.sql(mine + """
				, page AS (
				    SELECT f.playlist_id, f.my_role, la.last_added_at,
				           row_number() OVER (ORDER BY %1$s) AS n
				    FROM filtered f
				    JOIN playlist p ON p.playlist_id = f.playlist_id
				    LEFT JOIN LATERAL (SELECT max(pt.added_at) AS last_added_at FROM playlist_track pt
				                       WHERE pt.added_by = :me AND pt.playlist_id = f.playlist_id) la ON true
				    ORDER BY %1$s
				    LIMIT :limit OFFSET :offset
				)
				SELECT %2$s,
				       page.my_role, page.last_added_at,
				       EXISTS (SELECT 1 FROM playlist_follow fo
				               WHERE fo.playlist_id = page.playlist_id AND fo.user_id = :me) AS is_favorite,
				       CASE WHEN CAST(:track AS integer) IS NULL THEN NULL
				            ELSE EXISTS (SELECT 1 FROM playlist_track pt
				                         WHERE pt.track_id = :track AND pt.playlist_id = page.playlist_id)
				       END AS contains_track
				FROM page
				JOIN playlist_summary ps ON ps.playlist_id = page.playlist_id
				ORDER BY page.n
				""".formatted(sort.orderBy, SUMMARY_COLUMNS))
			.param("me", me)
			.param("role", role, Types.VARCHAR)
			.param("track", containsTrack, Types.INTEGER)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new MyPlaylist(PlaylistSql.summary(rs), rs.getString("my_role"),
					rs.getBoolean("is_favorite"), Rows.instant(rs, "last_added_at"), Rows.bool(rs, "contains_track")))
			.list();
		long total = this.jdbc.sql(mine + "SELECT count(*) FROM filtered")
			.param("me", me)
			.param("role", role, Types.VARCHAR)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	/**
	 * Random public playlists that the user neither owns nor has as a favorite (any public
	 * playlists when anonymous), from the schema's {@code random_playlists()}.
	 */
	List<PlaylistSummary> random(@Nullable Integer me, int limit, int[] exclude) {
		return this.jdbc.sql("""
				SELECT
				""" + SUMMARY_COLUMNS + """

				FROM random_playlists(:me, :limit, CAST(:exclude AS integer[])) WITH ORDINALITY AS r(playlist_id, n)
				JOIN playlist_summary ps ON ps.playlist_id = r.playlist_id
				ORDER BY r.n
				""")
			.param("me", me, Types.INTEGER)
			.param("limit", limit)
			.param("exclude", exclude)
			.query((rs, rowNum) -> PlaylistSql.summary(rs))
			.list();
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
			.query((rs, rowNum) -> PlaylistSql.summary(rs))
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
	 * @param name its name
	 * @param ownerId its owner
	 * @param version its current version
	 * @param visibility public or private (it follows favorites)
	 * @param canView {@code playlist_can_view(playlist, caller)}
	 * @param canEdit {@code playlist_can_edit(playlist, caller)}
	 * @param isCollaborator whether the caller is an editor or viewer
	 * @param me the caller, or null when anonymous
	 */
	record PlaylistAccess(int playlistId, String name, int ownerId, int version, String visibility, boolean canView, boolean canEdit,
			boolean isCollaborator, @Nullable Integer me) {

		boolean isOwner() {
			return this.me != null && this.me == this.ownerId;
		}

	}

	/** Orders of {@code GET /me/playlists?sort=}, over {@code filtered f}, {@code playlist p} and the lateral {@code la}. */
	enum MemberSort {

		/** A-Z, ignoring accents and case. */
		NAME("f_search_key(p.name), p.playlist_id"),

		/** The playlist the caller added to most recently first ("last used"); never-used ones last, newest first. */
		LAST_ADDED("la.last_added_at DESC NULLS LAST, p.created_at DESC, p.playlist_id DESC"),

		/** Newest first (the default). */
		CREATED("p.created_at DESC, p.playlist_id DESC");

		final String orderBy;

		MemberSort(String orderBy) {
			this.orderBy = orderBy;
		}

	}

}
