package com.oggmont.music.admin;

import com.oggmont.music.common.jdbc.ColumnChanges;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Catalog writes. Derived data (slugs, release years, composer text, album genres used
 * by tracks) is filled in by the schema's triggers.
 */
@Repository
class AdminCatalogRepository {

	private final JdbcClient jdbc;

	AdminCatalogRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Inserts an artist and returns its slug (generated when not given). */
	String insertArtist(AdminRequests.CreateArtistRequest artist, @Nullable String countryCode) {
		return this.jdbc.sql("""
				INSERT INTO artist (name, slug, artist_type, person_id, country_code, city, formed_year,
				                    disbanded_year, biography, image_url, musicbrainz_id)
				VALUES (:name, :slug, :artistType, :personId, :countryCode, :city, :formedYear,
				        :disbandedYear, :biography, :imageUrl, :musicbrainzId)
				RETURNING slug
				""")
			.param("name", artist.name().trim())
			.param("slug", artist.slug(), Types.VARCHAR)
			.param("artistType", artist.artistType(), Types.VARCHAR)
			.param("personId", artist.personId(), Types.INTEGER)
			.param("countryCode", countryCode, Types.CHAR)
			.param("city", artist.city(), Types.VARCHAR)
			.param("formedYear", artist.formedYear(), Types.SMALLINT)
			.param("disbandedYear", artist.disbandedYear(), Types.SMALLINT)
			.param("biography", artist.biography(), Types.VARCHAR)
			.param("imageUrl", artist.imageUrl(), Types.VARCHAR)
			.param("musicbrainzId", artist.musicbrainzId(), Types.OTHER)
			.query(String.class)
			.single();
	}

	/** Applies the changes and returns the artist's (possibly new) slug; empty if there is no such artist. */
	Optional<String> updateArtist(int artistId, ColumnChanges changes) {
		if (changes.isEmpty()) {
			return this.jdbc.sql("SELECT slug FROM artist WHERE artist_id = :artistId")
				.param("artistId", artistId)
				.query(String.class)
				.optional();
		}
		return changes
			.bind(this.jdbc.sql("UPDATE artist SET " + changes.setClause() + " WHERE artist_id = :artistId RETURNING slug"))
			.param("artistId", artistId)
			.query(String.class)
			.optional();
	}

	int insertAlbum(AdminRequests.CreateAlbumRequest album) {
		return this.jdbc.sql("""
				INSERT INTO album (title, edition, artist_id, slug, album_type, release_date, release_year, label_id,
				                   upc, cover_url, musicbrainz_id)
				VALUES (:title, :edition, :artistId, :slug, :albumType, :releaseDate, :releaseYear, :labelId,
				        :upc, :coverUrl, :musicbrainzId)
				RETURNING album_id
				""")
			.param("title", album.title().trim())
			.param("edition", album.edition(), Types.VARCHAR)
			.param("artistId", album.artistId())
			.param("slug", album.slug(), Types.VARCHAR)
			.param("albumType", album.albumType(), Types.VARCHAR)
			.param("releaseDate", album.releaseDate(), Types.DATE)
			.param("releaseYear", album.releaseYear(), Types.SMALLINT)
			.param("labelId", album.labelId(), Types.INTEGER)
			.param("upc", album.upc(), Types.VARCHAR)
			.param("coverUrl", album.coverUrl(), Types.VARCHAR)
			.param("musicbrainzId", album.musicbrainzId(), Types.OTHER)
			.query(Integer.class)
			.single();
	}

	void insertAlbumArtist(int albumId, int artistId, @Nullable String role, int position) {
		this.jdbc.sql("""
				INSERT INTO album_artist (album_id, artist_id, role, position)
				VALUES (:albumId, :artistId, coalesce(:role, 'main'), :position)
				""")
			.param("albumId", albumId)
			.param("artistId", artistId)
			.param("role", role, Types.VARCHAR)
			.param("position", position)
			.update();
	}

	/** Replaces an album's genres; the primary is moved in two steps, checked at commit. */
	void replaceGenres(int albumId, List<Integer> genreIds, @Nullable Integer primaryGenreId) {
		int[] ids = genreIds.stream().mapToInt(Integer::intValue).toArray();
		this.jdbc.sql("UPDATE album_genre SET is_primary = false WHERE album_id = :albumId AND is_primary")
			.param("albumId", albumId)
			.update();
		this.jdbc.sql("DELETE FROM album_genre WHERE album_id = :albumId AND NOT (genre_id = ANY (CAST(:ids AS integer[])))")
			.param("albumId", albumId)
			.param("ids", ids)
			.update();
		this.jdbc.sql("""
				INSERT INTO album_genre (album_id, genre_id)
				SELECT :albumId, g FROM unnest(CAST(:ids AS integer[])) AS g
				ON CONFLICT (album_id, genre_id) DO NOTHING
				""").param("albumId", albumId).param("ids", ids).update();
		if (primaryGenreId != null) {
			this.jdbc.sql("UPDATE album_genre SET is_primary = true WHERE album_id = :albumId AND genre_id = :genreId")
				.param("albumId", albumId)
				.param("genreId", primaryGenreId)
				.update();
		}
	}

	List<AlbumGenreRow> genres(int albumId) {
		return this.jdbc.sql("""
				SELECT g.genre_id, g.name, ag.is_primary
				FROM album_genre ag
				JOIN genre g ON g.genre_id = ag.genre_id
				WHERE ag.album_id = :albumId
				ORDER BY ag.is_primary DESC, g.name
				""")
			.param("albumId", albumId)
			.query((rs, rowNum) -> new AlbumGenreRow(rs.getInt("genre_id"), rs.getString("name"),
					rs.getBoolean("is_primary")))
			.list();
	}

	int insertTrack(int albumId, AdminRequests.TrackInput track) {
		return this.jdbc.sql("""
				INSERT INTO track (name, album_id, song_id, disc_number, track_number, genre_id, composer,
				                   milliseconds, bytes, isrc)
				VALUES (:name, :albumId, :songId, coalesce(:discNumber, 1), :trackNumber, :genreId, :composer,
				        :milliseconds, :bytes, :isrc)
				RETURNING track_id
				""")
			.param("name", track.name().trim())
			.param("albumId", albumId)
			.param("songId", track.songId(), Types.INTEGER)
			.param("discNumber", track.discNumber(), Types.SMALLINT)
			.param("trackNumber", track.trackNumber(), Types.SMALLINT)
			.param("genreId", track.genreId(), Types.INTEGER)
			.param("composer", track.composer(), Types.VARCHAR)
			.param("milliseconds", track.milliseconds())
			.param("bytes", track.bytes(), Types.INTEGER)
			.param("isrc", track.isrc(), Types.CHAR)
			.query(Integer.class)
			.single();
	}

	boolean songExists(int songId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM song WHERE song_id = :songId)")
			.param("songId", songId)
			.query(Boolean.class)
			.single();
	}

	boolean artistExists(int artistId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM artist WHERE artist_id = :artistId)")
			.param("artistId", artistId)
			.query(Boolean.class)
			.single();
	}

	/** Adds a songwriting credit; {@code track.composer} of every recording regenerates by trigger. */
	void insertCredit(int songId, AdminRequests.CreditInput credit) {
		this.jdbc.sql("""
				INSERT INTO credit (song_id, person_id, role, position, credited_as)
				VALUES (:songId, :personId, :role,
				        coalesce(:position, (SELECT coalesce(max(position), 0) + 1 FROM credit WHERE song_id = :songId)),
				        :creditedAs)
				""")
			.param("songId", songId)
			.param("personId", credit.personId())
			.param("role", credit.role())
			.param("position", credit.position(), Types.SMALLINT)
			.param("creditedAs", credit.creditedAs(), Types.VARCHAR)
			.update();
	}

	/**
	 * @param genreId {@code genre.genre_id}
	 * @param name the genre's name
	 * @param isPrimary whether it is the album's primary genre
	 */
	record AlbumGenreRow(int genreId, String name, boolean isPrimary) {
	}

}
