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
 * Albums: the {@code album_summary} view, {@code album_artist} and {@code track_detail}.
 */
@Repository
public class AlbumRepository {

	private final JdbcClient jdbc;

	AlbumRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<AlbumDetail> find(int albumId, @Nullable Integer me) {
		return this.jdbc.sql("""
				SELECT als.album_id, als.title, als.edition, als.slug, als.release_date, als.release_year,
				       als.album_type, als.label_id, als.label AS label_name, l.slug AS label_slug,
				       als.artist_id, als.artist_name, als.artist_slug, als.primary_genre, als.genres,
				       als.track_count, als.disc_count, als.total_milliseconds, als.upc, als.cover_url,
				       CASE WHEN CAST(:me AS integer) IS NULL THEN NULL
				            ELSE EXISTS (SELECT 1 FROM user_saved_album s WHERE s.user_id = :me AND s.album_id = als.album_id)
				       END AS is_saved_by_me
				FROM album_summary als
				LEFT JOIN label l ON l.label_id = als.label_id
				WHERE als.album_id = :albumId
				""")
			.param("me", me, Types.INTEGER)
			.param("albumId", albumId)
			.query((rs, rowNum) -> {
				Integer labelId = Rows.integer(rs, "label_id");
				return new AlbumDetail(rs.getInt("album_id"), rs.getString("title"), rs.getString("edition"),
						rs.getString("slug"), Rows.date(rs, "release_date"), Rows.integer(rs, "release_year"),
						rs.getString("album_type"),
						(labelId != null) ? new LabelRef(labelId, rs.getString("label_name"), rs.getString("label_slug"))
								: null,
						new ArtistRef(rs.getInt("artist_id"), rs.getString("artist_name"), rs.getString("artist_slug")),
						List.of(), rs.getString("primary_genre"), Rows.strings(rs, "genres"), rs.getInt("track_count"),
						rs.getInt("disc_count"), rs.getLong("total_milliseconds"), rs.getString("upc"),
						rs.getString("cover_url"), Rows.bool(rs, "is_saved_by_me"));
			})
			.optional()
			.map((album) -> album.withArtists(artists(albumId)));
	}

	/** The album credited to an artist, by both slugs: {@code /artists/the-beatles/albums/abbey-road}. */
	Optional<Integer> idBySlugs(String artistSlug, String albumSlug) {
		return this.jdbc.sql("""
				SELECT al.album_id
				FROM album al
				JOIN artist ar ON ar.artist_id = al.artist_id
				WHERE ar.slug = :artistSlug AND al.slug = :albumSlug
				""")
			.param("artistSlug", artistSlug)
			.param("albumSlug", albumSlug)
			.query(Integer.class)
			.optional();
	}

	public boolean exists(int albumId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM album WHERE album_id = :albumId)")
			.param("albumId", albumId)
			.query(Boolean.class)
			.single();
	}

	/** The track listing, in disc and track order. */
	public List<AlbumTrack> tracks(int albumId) {
		return this.jdbc.sql("""
				SELECT td.disc_number, td.track_number, td.track_id, td.track_name, td.milliseconds, td.composer,
				       td.song_id, td.track_artists
				FROM track_detail td
				WHERE td.album_id = :albumId
				ORDER BY td.disc_number, td.track_number NULLS LAST, td.track_id
				""")
			.param("albumId", albumId)
			.query((rs, rowNum) -> {
				long milliseconds = rs.getLong("milliseconds");
				return new AlbumTrack(rs.getInt("disc_number"), Rows.integer(rs, "track_number"), rs.getInt("track_id"),
						rs.getString("track_name"), milliseconds, Durations.format(milliseconds),
						rs.getString("composer"), Rows.integer(rs, "song_id"), Rows.strings(rs, "track_artists"));
			})
			.list();
	}

	private List<AlbumArtist> artists(int albumId) {
		return this.jdbc.sql("""
				SELECT a.artist_id, a.name, a.slug, aa.role
				FROM album_artist aa
				JOIN artist a ON a.artist_id = aa.artist_id
				WHERE aa.album_id = :albumId
				ORDER BY aa.position, a.name
				""")
			.param("albumId", albumId)
			.query((rs, rowNum) -> new AlbumArtist(rs.getInt("artist_id"), rs.getString("name"), rs.getString("slug"),
					rs.getString("role")))
			.list();
	}

	/**
	 * An album page.
	 *
	 * @param albumId {@code album.album_id}
	 * @param title the album's title
	 * @param edition e.g. {@code "Deluxe"}
	 * @param slug URL key, unique per credited artist
	 * @param releaseDate exact release date, when known
	 * @param releaseYear release year, when known
	 * @param albumType studio, live, compilation...
	 * @param label record label
	 * @param artist the artist as credited on the cover
	 * @param artists the individual artists and their roles ({@code album_artist})
	 * @param primaryGenre the primary genre
	 * @param genres all genres, primary first
	 * @param trackCount number of tracks
	 * @param discCount number of discs
	 * @param durationMs total running time
	 * @param upc barcode
	 * @param coverUrl cover image
	 * @param isSavedByMe whether the caller saved it; only sent with a token
	 */
	public record AlbumDetail(int albumId, String title, @Nullable String edition, String slug, @Nullable LocalDate releaseDate,
			@Nullable Integer releaseYear, @Nullable String albumType, @Nullable LabelRef label, ArtistRef artist,
			List<AlbumArtist> artists, @Nullable String primaryGenre, List<String> genres, int trackCount, int discCount,
			long durationMs, @Nullable String upc, @Nullable String coverUrl,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean isSavedByMe) {

		AlbumDetail withArtists(List<AlbumArtist> artists) {
			return new AlbumDetail(this.albumId, this.title, this.edition, this.slug, this.releaseDate, this.releaseYear,
					this.albumType, this.label, this.artist, artists, this.primaryGenre, this.genres, this.trackCount,
					this.discCount, this.durationMs, this.upc, this.coverUrl, this.isSavedByMe);
		}

	}

	/**
	 * @param labelId {@code label.label_id}
	 * @param name the label's name
	 * @param slug URL key ({@code /labels/{slug}})
	 */
	public record LabelRef(int labelId, String name, String slug) {
	}

	/**
	 * @param artistId {@code artist.artist_id}
	 * @param name the artist's name
	 * @param slug URL key
	 * @param role main, featured, conductor...
	 */
	public record AlbumArtist(int artistId, String name, String slug, String role) {
	}

	/**
	 * @param discNumber disc
	 * @param trackNumber position on the disc
	 * @param trackId {@code track.track_id}
	 * @param name the track's name
	 * @param durationMs running time
	 * @param duration running time for display
	 * @param composer songwriters as display text
	 * @param songId the composition ({@code /songs/{id}})
	 * @param artists who performs it
	 */
	public record AlbumTrack(int discNumber, @Nullable Integer trackNumber, int trackId, String name, long durationMs,
			String duration, @Nullable String composer, @Nullable Integer songId, List<String> artists) {
	}

}
