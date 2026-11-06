package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.common.Durations;
import com.oggmont.music.common.jdbc.Rows;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Songs (compositions), their songwriting credits and their recordings.
 */
@Repository
public class SongRepository {

	private final JdbcClient jdbc;

	SongRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<SongDetail> find(int songId) {
		return this.jdbc.sql("""
				SELECT s.song_id, s.title, s.first_release_year, s.language, s.is_traditional, s.iswc,
				       ar.artist_id, ar.name AS artist_name, ar.slug AS artist_slug
				FROM song s
				LEFT JOIN artist ar ON ar.artist_id = s.artist_id
				WHERE s.song_id = :songId
				""")
			.param("songId", songId)
			.query((rs, rowNum) -> {
				Integer artistId = Rows.integer(rs, "artist_id");
				return new SongDetail(rs.getInt("song_id"), rs.getString("title"),
						Rows.integer(rs, "first_release_year"), rs.getString("language"),
						rs.getBoolean("is_traditional"), rs.getString("iswc"),
						(artistId != null)
								? new ArtistRef(artistId, rs.getString("artist_name"), rs.getString("artist_slug")) : null,
						List.of());
			})
			.optional()
			.map((song) -> song.withCredits(credits(songId)));
	}

	/** Every recording of the song, oldest first (example 7 in examples.sql). */
	List<Recording> recordings(int songId) {
		return this.jdbc.sql("""
				SELECT td.track_id, td.track_name, td.album_id, td.album_title, td.album_slug, td.artist_slug,
				       td.release_year, td.milliseconds
				FROM track_detail td
				WHERE td.song_id = :songId
				ORDER BY td.release_year NULLS LAST, td.track_id
				""")
			.param("songId", songId)
			.query((rs, rowNum) -> {
				long milliseconds = rs.getLong("milliseconds");
				return new Recording(rs.getInt("track_id"), rs.getString("track_name"), rs.getInt("album_id"),
						rs.getString("album_title"), rs.getString("album_slug"), rs.getString("artist_slug"),
						Rows.integer(rs, "release_year"), milliseconds, Durations.format(milliseconds));
			})
			.list();
	}

	private List<Credit> credits(int songId) {
		return this.jdbc.sql("""
				SELECT c.person_id, p.name, c.role, c.credited_as
				FROM credit c
				JOIN person p ON p.person_id = c.person_id
				WHERE c.song_id = :songId
				ORDER BY c.position, c.credit_id
				""")
			.param("songId", songId)
			.query((rs, rowNum) -> new Credit(rs.getInt("person_id"), rs.getString("name"), rs.getString("role"),
					rs.getString("credited_as")))
			.list();
	}

	/**
	 * A song (composition) with its credits.
	 *
	 * @param songId {@code song.song_id}
	 * @param title the song's title
	 * @param firstReleaseYear year of its earliest recording in the catalog
	 * @param language ISO 639 code of the lyrics ({@code zxx}: instrumental)
	 * @param isTraditional a traditional tune (its credits are arrangers)
	 * @param iswc International Standard Musical Work Code
	 * @param artist who recorded it first
	 * @param credits songwriters, in credit order
	 */
	public record SongDetail(int songId, String title, @Nullable Integer firstReleaseYear, @Nullable String language,
			boolean isTraditional, @Nullable String iswc, @Nullable ArtistRef artist, List<Credit> credits) {

		SongDetail withCredits(List<Credit> credits) {
			return new SongDetail(this.songId, this.title, this.firstReleaseYear, this.language, this.isTraditional,
					this.iswc, this.artist, credits);
		}

	}

	/**
	 * @param personId {@code person.person_id}
	 * @param name the person's name
	 * @param role writer, composer, lyricist...
	 * @param creditedAs the name as printed, when it differs ("Richard Starkey")
	 */
	public record Credit(int personId, String name, String role,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String creditedAs) {
	}

	/**
	 * @param trackId {@code track.track_id}
	 * @param name the track's name
	 * @param albumId the album
	 * @param albumTitle the album's title
	 * @param albumSlug the album's slug
	 * @param artistSlug the album artist's slug
	 * @param releaseYear the album's release year
	 * @param durationMs running time
	 * @param duration running time for display
	 */
	record Recording(int trackId, String name, int albumId, String albumTitle, String albumSlug, String artistSlug,
			@Nullable Integer releaseYear, long durationMs, String duration) {
	}

	/**
	 * @param song the song
	 * @param items its recordings
	 * @param total how many there are
	 */
	record Recordings(SongDetail song, List<Recording> items, int total) {
	}

}
