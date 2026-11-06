package com.oggmont.music.playlist;

import com.oggmont.music.catalog.CatalogSql;
import com.oggmont.music.common.jdbc.Rows;
import java.sql.Types;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * A playlist's entries ({@code playlist_track}).
 * <p>
 * Positions are 1..n. The primary key is checked at the end of each statement, so a
 * single {@code UPDATE} can shift entries. Every statement that changes the track list
 * raises {@code playlist.version} through a trigger (a statement that combines two, like
 * delete-and-close-the-gap, raises it twice), so callers read the new version afterwards
 * instead of computing it.
 */
@Repository
class PlaylistTrackRepository {

	private final JdbcClient jdbc;

	PlaylistTrackRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Entries in order, after {@code afterPosition} when given (keyset paging). */
	List<PlaylistEntry> entries(int playlistId, @Nullable Integer me, int limit, int offset,
			@Nullable Integer afterPosition) {
		return this.jdbc.sql("""
				WITH page AS (
				    SELECT pt.position, pt.track_id, pt.added_by, pt.added_at
				    FROM playlist_track pt
				    WHERE pt.playlist_id = :playlistId AND pt.position > :afterPosition
				    ORDER BY pt.position
				    LIMIT :limit OFFSET :offset
				)
				SELECT page.position, page.added_at,
				       u.user_id AS added_by_id, u.username AS added_by_username, u.display_name AS added_by_name,
				       """ + CatalogSql.TRACK_CARD_COLUMNS + ",\n" + CatalogSql.IS_LIKED + """

				FROM page
				JOIN track_detail td ON td.track_id = page.track_id
				LEFT JOIN app_user u ON u.user_id = page.added_by
				ORDER BY page.position
				""")
			.param("me", me, Types.INTEGER)
			.param("playlistId", playlistId)
			.param("afterPosition", (afterPosition != null) ? afterPosition : 0)
			.param("limit", limit)
			.param("offset", offset)
			.query((rs, rowNum) -> new PlaylistEntry(rs.getInt("position"), Rows.requiredInstant(rs, "added_at"),
					PlaylistRepository.addedBy(rs), CatalogSql.trackCard(rs, true)))
			.list();
	}

	long count(int playlistId) {
		return this.jdbc.sql("SELECT count(*) FROM playlist_track WHERE playlist_id = :playlistId")
			.param("playlistId", playlistId)
			.query(Long.class)
			.single();
	}

	int maxPosition(int playlistId) {
		return this.jdbc.sql("SELECT coalesce(max(position), 0) FROM playlist_track WHERE playlist_id = :playlistId")
			.param("playlistId", playlistId)
			.query(Integer.class)
			.single();
	}

	int version(int playlistId) {
		return this.jdbc.sql("SELECT version FROM playlist WHERE playlist_id = :playlistId")
			.param("playlistId", playlistId)
			.query(Integer.class)
			.single();
	}

	/** Makes room for {@code count} entries at {@code position}. */
	void shift(int playlistId, int position, int count) {
		this.jdbc.sql("""
				UPDATE playlist_track SET position = position + :count
				WHERE playlist_id = :playlistId AND position >= :position
				""")
			.param("count", count)
			.param("playlistId", playlistId)
			.param("position", position)
			.update();
	}

	/** Inserts the tracks, in order, from {@code startPosition}. */
	List<Inserted> insert(int playlistId, List<Integer> trackIds, int startPosition, int addedBy) {
		return this.jdbc.sql("""
				INSERT INTO playlist_track (playlist_id, position, track_id, added_by)
				SELECT :playlistId, :startPosition + t.n - 1, t.id, :addedBy
				FROM unnest(CAST(:trackIds AS integer[])) WITH ORDINALITY AS t(id, n)
				RETURNING position, track_id
				""")
			.param("playlistId", playlistId)
			.param("startPosition", startPosition)
			.param("trackIds", trackIds.stream().mapToInt(Integer::intValue).toArray())
			.param("addedBy", addedBy)
			.query((rs, rowNum) -> new Inserted(rs.getInt("position"), rs.getInt("track_id")))
			.list()
			.stream()
			.sorted(Comparator.comparingInt(Inserted::position))
			.toList();
	}

	/** Copies every entry of one playlist to the end of another, in order. */
	void copy(int sourceId, int targetId, int addedBy) {
		this.jdbc.sql("""
				INSERT INTO playlist_track (playlist_id, position, track_id, added_by)
				SELECT :targetId, row_number() OVER (ORDER BY position), track_id, :addedBy
				FROM playlist_track
				WHERE playlist_id = :sourceId
				""")
			.param("targetId", targetId)
			.param("addedBy", addedBy)
			.param("sourceId", sourceId)
			.update();
	}

	/**
	 * Moves the entry at {@code from} to {@code to}; the entries in between shift by one.
	 * One statement, so the temporary duplicate positions never fail the key check.
	 */
	void move(int playlistId, int from, int to) {
		boolean up = to < from;
		this.jdbc.sql("""
				UPDATE playlist_track
				SET position = CASE WHEN position = :from THEN :to ELSE position + :step END
				WHERE playlist_id = :playlistId AND position BETWEEN :low AND :high
				""")
			.param("from", from)
			.param("to", to)
			.param("step", up ? 1 : -1)
			.param("playlistId", playlistId)
			.param("low", Math.min(from, to))
			.param("high", Math.max(from, to))
			.update();
	}

	/** Removes the entry at a position and closes the gap. Empty if there is no such entry. */
	Optional<TrackListChanges.RemovedEntry> removeAt(int playlistId, int position) {
		return this.jdbc.sql("""
				WITH gone AS (
				    DELETE FROM playlist_track
				    WHERE playlist_id = :playlistId AND position = :position
				    RETURNING position, track_id
				), shifted AS (
				    UPDATE playlist_track SET position = position - 1
				    WHERE playlist_id = :playlistId AND position > (SELECT position FROM gone)
				    RETURNING 1
				)
				SELECT position, track_id, (SELECT count(*) FROM shifted) AS shifted FROM gone
				""")
			.param("playlistId", playlistId)
			.param("position", position)
			.query((rs, rowNum) -> new TrackListChanges.RemovedEntry(rs.getInt("position"), rs.getInt("track_id")))
			.optional();
	}

	/** Removes every entry of a track; returns how many. */
	int removeTrack(int playlistId, int trackId) {
		return this.jdbc.sql("DELETE FROM playlist_track WHERE playlist_id = :playlistId AND track_id = :trackId")
			.param("playlistId", playlistId)
			.param("trackId", trackId)
			.update();
	}

	/** Renumbers the entries 1..n, keeping their order. */
	void renumber(int playlistId) {
		this.jdbc.sql("""
				UPDATE playlist_track pt
				SET position = r.new_position
				FROM (SELECT position, row_number() OVER (ORDER BY position) AS new_position
				      FROM playlist_track WHERE playlist_id = :playlistId) r
				WHERE pt.playlist_id = :playlistId AND pt.position = r.position AND pt.position <> r.new_position
				""")
			.param("playlistId", playlistId)
			.update();
	}

	/**
	 * @param position where the entry was inserted
	 * @param trackId its track
	 */
	record Inserted(int position, int trackId) {
	}

}
