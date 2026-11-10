package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.oggmont.music.common.Durations;
import com.oggmont.music.common.jdbc.Rows;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Tracks (recordings), from the {@code track_detail} view.
 */
@Repository
class TrackRepository {

	private final JdbcClient jdbc;

	TrackRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<TrackDetail> find(int trackId, @Nullable Integer me) {
		return this.jdbc.sql("""
				SELECT td.track_id, td.track_name, td.disc_number, td.track_number, td.milliseconds, td.composer,
				       td.isrc, td.song_id, td.song_title, td.album_id, td.album_title, td.album_slug, td.album_edition,
				       td.release_date, td.release_year, td.album_type, td.artist_id, td.artist_name, td.artist_slug,
				       td.track_artists, td.genre_id, td.genre_name, td.has_own_genre,
				""" + CatalogSql.IS_LIKED + """

				FROM track_detail td
				WHERE td.track_id = :trackId
				""")
			.param("me", me, Types.INTEGER)
			.param("trackId", trackId)
			.query((rs, rowNum) -> {
				long milliseconds = rs.getLong("milliseconds");
				Integer songId = Rows.integer(rs, "song_id");
				Integer genreId = Rows.integer(rs, "genre_id");
				return new TrackDetail(rs.getInt("track_id"), rs.getString("track_name"), rs.getInt("disc_number"),
						Rows.integer(rs, "track_number"), milliseconds, Durations.format(milliseconds),
						rs.getString("composer"), rs.getString("isrc"),
						(songId != null) ? new SongRef(songId, rs.getString("song_title")) : null,
						new AlbumInfo(rs.getInt("album_id"), rs.getString("album_title"), rs.getString("album_slug"),
								rs.getString("album_edition"), Rows.date(rs, "release_date"),
								Rows.integer(rs, "release_year"), rs.getString("album_type")),
						new ArtistRef(rs.getInt("artist_id"), rs.getString("artist_name"), rs.getString("artist_slug")),
						Rows.strings(rs, "track_artists"),
						(genreId != null) ? new GenreRef(genreId, rs.getString("genre_name")) : null,
						rs.getBoolean("has_own_genre"), Rows.bool(rs, "is_liked"));
			})
			.optional();
	}

	/**
	 * Song search with the schema's {@code search_tracks()}: track name, artists and album
	 * title, accent-insensitive, best first. Every row carries the total.
	 */
	TrackSearchResults search(String query, @Nullable Integer me, int limit, int offset) {
		record Row(TrackHit hit, long total) {
		}
		List<Row> rows = this.jdbc.sql("""
				SELECT s.score, s.total,
				""" + CatalogSql.TRACK_CARD_COLUMNS + ",\n" + CatalogSql.IS_LIKED + """

				FROM search_tracks(:q, :limit, :offset) WITH ORDINALITY AS s(track_id, score, total, n)
				JOIN track_detail td ON td.track_id = s.track_id
				ORDER BY s.n
				""")
			.param("q", query)
			.param("me", me, Types.INTEGER)
			.param("limit", limit)
			.param("offset", offset)
			.query((rs, rowNum) -> new Row(new TrackHit(CatalogSql.trackCard(rs, true), rs.getDouble("score")),
					rs.getLong("total")))
			.list();
		long total = rows.isEmpty() ? countMatches(query) : rows.getFirst().total();
		return new TrackSearchResults(query, rows.stream().map(Row::hit).toList(), limit, offset, total);
	}

	/** The number of matches, for a page past the last one (which has no rows to read it from). */
	private long countMatches(String query) {
		return this.jdbc.sql("SELECT coalesce(max(total), 0) FROM search_tracks(:q, 1, 0)")
			.param("q", query)
			.query(Long.class)
			.single();
	}

	/**
	 * Random tracks from the schema's {@code random_tracks()}, which can come back a
	 * little short (gaps in the IDs); asks again for the rest, a few times at most.
	 */
	List<TrackCard> random(@Nullable Integer me, int limit, Set<Integer> exclude) {
		Set<Integer> skip = new LinkedHashSet<>(exclude);
		List<Integer> picked = new ArrayList<>();
		for (int attempt = 0; attempt < 3 && picked.size() < limit; attempt++) {
			List<Integer> ids = this.jdbc.sql("SELECT r FROM random_tracks(:limit, CAST(:exclude AS integer[])) r")
				.param("limit", limit - picked.size())
				.param("exclude", skip.stream().mapToInt(Integer::intValue).toArray())
				.query(Integer.class)
				.list();
			picked.addAll(ids);
			skip.addAll(ids);
		}
		return this.jdbc.sql("""
				SELECT
				""" + CatalogSql.TRACK_CARD_COLUMNS + ",\n" + CatalogSql.IS_LIKED + """

				FROM unnest(CAST(:ids AS integer[])) WITH ORDINALITY AS r(track_id, n)
				JOIN track_detail td ON td.track_id = r.track_id
				ORDER BY r.n
				""")
			.param("ids", picked.stream().mapToInt(Integer::intValue).toArray())
			.param("me", me, Types.INTEGER)
			.query((rs, rowNum) -> CatalogSql.trackCard(rs, true))
			.list();
	}

	/**
	 * Results of {@code GET /tracks?q=}.
	 *
	 * @param query the search, as searched
	 * @param items matching tracks, best first
	 * @param limit the page size
	 * @param offset how many matches were skipped
	 * @param total how many tracks match
	 */
	record TrackSearchResults(String query, List<TrackHit> items, int limit, int offset, long total) {
	}

	/**
	 * A matching track: the track card's fields, plus its relevance.
	 *
	 * @param track the track (its fields appear at the top level)
	 * @param score relevance: 1 for the whole name, 0.9 its start, 0.8 anywhere in the name,
	 * 0.5 to 0.75 for matches in the artist or album only
	 */
	record TrackHit(@JsonUnwrapped TrackCard track, double score) {
	}

	/**
	 * A track page.
	 *
	 * @param trackId {@code track.track_id}
	 * @param name the track's name
	 * @param discNumber disc
	 * @param trackNumber position on the disc
	 * @param durationMs running time
	 * @param duration running time for display
	 * @param composer songwriters as display text (generated from the song's credits)
	 * @param isrc International Standard Recording Code
	 * @param song the composition it records
	 * @param album the album it is on
	 * @param artist the album's credited artist
	 * @param artists who performs this track
	 * @param genre its effective genre (its own, else the album's primary genre)
	 * @param hasOwnGenre whether the genre differs from the album's
	 * @param isLiked whether the caller liked it; only sent with a token
	 */
	record TrackDetail(int trackId, String name, int discNumber, @Nullable Integer trackNumber, long durationMs,
			String duration, @Nullable String composer, @Nullable String isrc, @Nullable SongRef song, AlbumInfo album,
			ArtistRef artist, List<String> artists, @Nullable GenreRef genre, boolean hasOwnGenre,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean isLiked) {
	}

	record SongRef(int songId, String title) {
	}

	record AlbumInfo(int albumId, String title, String slug, @Nullable String edition, @Nullable LocalDate releaseDate,
			@Nullable Integer releaseYear, @Nullable String albumType) {
	}

	record GenreRef(int genreId, String name) {
	}

}
