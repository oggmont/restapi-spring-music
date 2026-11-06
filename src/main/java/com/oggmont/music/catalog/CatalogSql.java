package com.oggmont.music.catalog;

import com.oggmont.music.common.Durations;
import com.oggmont.music.common.jdbc.Rows;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/**
 * SQL column lists and row mapping for the catalog cards, shared by every query that
 * embeds them. Each list expects its view under a fixed alias:
 * {@code track_detail td}, {@code album_summary als}, {@code artist_summary ars}.
 */
public final class CatalogSql {

	/** {@link TrackCard} columns from {@code track_detail td}. */
	public static final String TRACK_CARD_COLUMNS = """
			td.track_id, td.track_name, td.milliseconds, td.track_artists,
			td.album_id, td.album_title, td.album_slug, td.artist_slug""";

	/**
	 * {@code is_liked} for the caller bound as {@code :me}; NULL (left out of the JSON)
	 * for anonymous requests.
	 */
	public static final String IS_LIKED = """
			CASE WHEN CAST(:me AS integer) IS NULL THEN NULL
			     ELSE EXISTS (SELECT 1 FROM user_saved_track s WHERE s.user_id = :me AND s.track_id = td.track_id)
			END AS is_liked""";

	/** {@link AlbumCard} columns from {@code album_summary als}. */
	public static final String ALBUM_CARD_COLUMNS = """
			als.album_id, als.title, als.edition, als.slug, als.release_date, als.release_year, als.album_type,
			als.artist_id, als.artist_name, als.artist_slug, als.primary_genre, als.genres,
			als.track_count, als.disc_count, als.total_milliseconds, als.cover_url""";

	/** {@link ArtistCard} columns from {@code artist_summary ars}. */
	public static final String ARTIST_CARD_COLUMNS = """
			ars.artist_id, ars.name, ars.slug, ars.artist_type, ars.country_code, ars.formed_year,
			ars.disbanded_year, ars.image_url, ars.follower_count, ars.album_count, ars.track_count""";

	private CatalogSql() {
	}

	/** A track card; {@code withLiked} when the query selected {@link #IS_LIKED}. */
	public static TrackCard trackCard(ResultSet rs, boolean withLiked) throws SQLException {
		long milliseconds = rs.getLong("milliseconds");
		@Nullable Boolean isLiked = withLiked ? Rows.bool(rs, "is_liked") : null;
		return new TrackCard(rs.getInt("track_id"), rs.getString("track_name"), milliseconds,
				Durations.format(milliseconds), Rows.strings(rs, "track_artists"),
				new AlbumRef(rs.getInt("album_id"), rs.getString("album_title"), rs.getString("album_slug"),
						rs.getString("artist_slug")),
				isLiked);
	}

	public static AlbumCard albumCard(ResultSet rs) throws SQLException {
		return new AlbumCard(rs.getInt("album_id"), rs.getString("title"), rs.getString("edition"),
				rs.getString("slug"), Rows.date(rs, "release_date"), Rows.integer(rs, "release_year"),
				rs.getString("album_type"),
				new ArtistRef(rs.getInt("artist_id"), rs.getString("artist_name"), rs.getString("artist_slug")),
				rs.getString("primary_genre"), Rows.strings(rs, "genres"), rs.getInt("track_count"),
				rs.getInt("disc_count"), rs.getLong("total_milliseconds"), rs.getString("cover_url"), null);
	}

	public static ArtistCard artistCard(ResultSet rs) throws SQLException {
		return new ArtistCard(rs.getInt("artist_id"), rs.getString("name"), rs.getString("slug"),
				rs.getString("artist_type"), rs.getString("country_code"), Rows.integer(rs, "formed_year"),
				Rows.integer(rs, "disbanded_year"), rs.getString("image_url"), rs.getInt("follower_count"),
				rs.getLong("album_count"), rs.getLong("track_count"));
	}

}
