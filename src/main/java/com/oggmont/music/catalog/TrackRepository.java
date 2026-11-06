package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.common.Durations;
import com.oggmont.music.common.jdbc.Rows;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
