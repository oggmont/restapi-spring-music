package com.oggmont.music.library;

import com.oggmont.music.catalog.AlbumCard;
import com.oggmont.music.catalog.CatalogSql;
import com.oggmont.music.catalog.TrackCard;
import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The caller's library: {@code user_saved_track} (liked tracks) and
 * {@code user_saved_album}.
 */
@Repository
class LibraryRepository {

	private final JdbcClient jdbc;

	LibraryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Liked tracks, newest first. {@code before} (plus {@code beforeId} to break ties
	 * between items saved at the same instant) is a keyset cursor: items saved before it.
	 */
	Page<SavedTrack> savedTracks(int me, PageParams page, @Nullable Instant before, @Nullable Integer beforeId) {
		String cursor = cursor("s.track_id", before, beforeId);
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				WITH page AS (
				    SELECT s.track_id, s.saved_at
				    FROM user_saved_track s
				    WHERE s.user_id = :me""" + cursor + """

				    ORDER BY s.saved_at DESC, s.track_id DESC
				    LIMIT :limit OFFSET :offset
				)
				SELECT page.saved_at, """ + CatalogSql.TRACK_CARD_COLUMNS + """

				FROM page
				JOIN track_detail td ON td.track_id = page.track_id
				ORDER BY page.saved_at DESC, page.track_id DESC
				""");
		List<SavedTrack> items = bindCursor(statement, before, beforeId).param("me", me)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new SavedTrack(Rows.requiredInstant(rs, "saved_at"), CatalogSql.trackCard(rs, false)))
			.list();
		return Page.of(items, page, count("SELECT count(*) FROM user_saved_track WHERE user_id = :me", me));
	}

	/** Saved albums, newest first, with the same keyset cursor as {@link #savedTracks}. */
	Page<SavedAlbum> savedAlbums(int me, PageParams page, @Nullable Instant before, @Nullable Integer beforeId) {
		String cursor = cursor("s.album_id", before, beforeId);
		JdbcClient.StatementSpec statement = this.jdbc.sql("""
				WITH page AS (
				    SELECT s.album_id, s.saved_at
				    FROM user_saved_album s
				    WHERE s.user_id = :me""" + cursor + """

				    ORDER BY s.saved_at DESC, s.album_id DESC
				    LIMIT :limit OFFSET :offset
				)
				SELECT page.saved_at, """ + CatalogSql.ALBUM_CARD_COLUMNS + """

				FROM page
				JOIN album_summary als ON als.album_id = page.album_id
				ORDER BY page.saved_at DESC, page.album_id DESC
				""");
		List<SavedAlbum> items = bindCursor(statement, before, beforeId).param("me", me)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new SavedAlbum(Rows.requiredInstant(rs, "saved_at"), CatalogSql.albumCard(rs)))
			.list();
		return Page.of(items, page, count("SELECT count(*) FROM user_saved_album WHERE user_id = :me", me));
	}

	/** Likes a track; 0 if it was already liked or doesn't exist. */
	int saveTrack(int me, int trackId) {
		return this.jdbc.sql("""
				INSERT INTO user_saved_track (user_id, track_id)
				SELECT :me, track_id FROM track WHERE track_id = :trackId
				ON CONFLICT DO NOTHING
				""").param("me", me).param("trackId", trackId).update();
	}

	int unsaveTrack(int me, int trackId) {
		return this.jdbc.sql("DELETE FROM user_saved_track WHERE user_id = :me AND track_id = :trackId")
			.param("me", me)
			.param("trackId", trackId)
			.update();
	}

	int saveAlbum(int me, int albumId) {
		return this.jdbc.sql("""
				INSERT INTO user_saved_album (user_id, album_id)
				SELECT :me, album_id FROM album WHERE album_id = :albumId
				ON CONFLICT DO NOTHING
				""").param("me", me).param("albumId", albumId).update();
	}

	int unsaveAlbum(int me, int albumId) {
		return this.jdbc.sql("DELETE FROM user_saved_album WHERE user_id = :me AND album_id = :albumId")
			.param("me", me)
			.param("albumId", albumId)
			.update();
	}

	boolean trackExists(int trackId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM track WHERE track_id = :id)")
			.param("id", trackId)
			.query(Boolean.class)
			.single();
	}

	boolean albumExists(int albumId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM album WHERE album_id = :id)")
			.param("id", albumId)
			.query(Boolean.class)
			.single();
	}

	/** Which of these tracks the user liked, in the order asked: {@code {"3674": true, ...}}. */
	Map<String, Boolean> containsTracks(int me, List<Integer> trackIds) {
		Map<String, Boolean> result = new LinkedHashMap<>();
		this.jdbc.sql("""
				SELECT t.id, EXISTS (SELECT 1 FROM user_saved_track s WHERE s.user_id = :me AND s.track_id = t.id) AS liked
				FROM unnest(CAST(:ids AS integer[])) WITH ORDINALITY AS t(id, n)
				ORDER BY t.n
				""")
			.param("me", me)
			.param("ids", trackIds.stream().mapToInt(Integer::intValue).toArray())
			.query((rs) -> {
				result.put(Integer.toString(rs.getInt("id")), rs.getBoolean("liked"));
			});
		return result;
	}

	private long count(String sql, int me) {
		return this.jdbc.sql(sql).param("me", me).query(Long.class).single();
	}

	private static String cursor(String idColumn, @Nullable Instant before, @Nullable Integer beforeId) {
		if (before == null) {
			return "";
		}
		return (beforeId != null) ? " AND (s.saved_at, " + idColumn + ") < (:before, :beforeId)"
				: " AND s.saved_at < :before";
	}

	private static JdbcClient.StatementSpec bindCursor(JdbcClient.StatementSpec statement, @Nullable Instant before,
			@Nullable Integer beforeId) {
		if (before != null) {
			statement.param("before", Rows.param(before));
			if (beforeId != null) {
				statement.param("beforeId", beforeId);
			}
		}
		return statement;
	}

	/**
	 * @param savedAt when it was liked
	 * @param track the track
	 */
	record SavedTrack(Instant savedAt, TrackCard track) {
	}

	/**
	 * @param savedAt when it was saved
	 * @param album the album
	 */
	record SavedAlbum(Instant savedAt, AlbumCard album) {
	}

}
