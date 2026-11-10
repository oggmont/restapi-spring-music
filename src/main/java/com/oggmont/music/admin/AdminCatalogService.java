package com.oggmont.music.admin;

import com.oggmont.music.catalog.AlbumRepository;
import com.oggmont.music.catalog.ArtistRepository;
import com.oggmont.music.catalog.SongRepository;
import com.oggmont.music.catalog.TrackSearchIndex;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.jdbc.ColumnChanges;
import com.oggmont.music.common.web.PartialUpdate;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Catalog administration. Some rules are checked by the database at commit (an album
 * lists its credited artist; exactly one primary genre), so related rows are written in
 * one transaction, and a violation surfaces as {@code 422} when it commits. Writes that
 * change what song search matches (artist names, new tracks) refresh its view afterwards.
 */
@Service
class AdminCatalogService {

	private final AdminCatalogRepository catalog;

	private final ArtistRepository artists;

	private final AlbumRepository albums;

	private final SongRepository songs;

	private final TransactionTemplate transactions;

	private final TrackSearchIndex trackSearch;

	AdminCatalogService(AdminCatalogRepository catalog, ArtistRepository artists, AlbumRepository albums,
			SongRepository songs, TransactionTemplate transactions, TrackSearchIndex trackSearch) {
		this.catalog = catalog;
		this.artists = artists;
		this.albums = albums;
		this.songs = songs;
		this.transactions = transactions;
		this.trackSearch = trackSearch;
	}

	ArtistRepository.ArtistDetail createArtist(AdminRequests.CreateArtistRequest request) {
		String slug = this.transactions.execute((status) -> this.catalog.insertArtist(request, upper(request.countryCode())));
		return this.artists.find(required(slug), null).orElseThrow();
	}

	ArtistRepository.ArtistDetail updateArtist(int artistId, PartialUpdate<AdminRequests.UpdateArtistRequest> patch) {
		patch.rejectNulls("name");
		AdminRequests.UpdateArtistRequest request = patch.value();
		ColumnChanges changes = new ColumnChanges();
		if (patch.has("name")) {
			changes.set("name", Objects.requireNonNull(request.name()).trim(), Types.VARCHAR);
		}
		set(patch, changes, "slug", request.slug(), Types.VARCHAR); // null: the trigger regenerates it from the name
		set(patch, changes, "artist_type", request.artistType(), Types.VARCHAR);
		set(patch, changes, "person_id", request.personId(), Types.INTEGER);
		set(patch, changes, "country_code", upper(request.countryCode()), Types.CHAR);
		set(patch, changes, "city", request.city(), Types.VARCHAR);
		set(patch, changes, "formed_year", request.formedYear(), Types.SMALLINT);
		set(patch, changes, "disbanded_year", request.disbandedYear(), Types.SMALLINT);
		set(patch, changes, "biography", request.biography(), Types.VARCHAR);
		set(patch, changes, "image_url", request.imageUrl(), Types.VARCHAR);
		set(patch, changes, "musicbrainz_id", request.musicbrainzId(), Types.OTHER);
		String slug = this.transactions.execute((status) -> this.catalog.updateArtist(artistId, changes)
			.orElseThrow(() -> ApiException.notFound("No artist " + artistId + ".")));
		if (patch.has("name")) {
			this.trackSearch.refresh();
		}
		return this.artists.find(required(slug), null).orElseThrow();
	}

	/** The album, its artists and its genres, in one transaction. */
	AlbumRepository.AlbumDetail createAlbum(AdminRequests.CreateAlbumRequest request) {
		List<AdminRequests.AlbumGenreInput> genres = (request.genres() != null) ? request.genres() : List.of();
		requireOnePrimary(genres);
		Integer albumId = this.transactions.execute((status) -> {
			int id = this.catalog.insertAlbum(request);
			for (int i = 0; i < request.artists().size(); i++) {
				AdminRequests.AlbumArtistInput artist = request.artists().get(i);
				this.catalog.insertAlbumArtist(id, artist.artistId(), artist.role(), i + 1);
			}
			if (!genres.isEmpty()) {
				this.catalog.replaceGenres(id, genreIds(genres), primaryGenre(genres));
			}
			return id;
		});
		return this.albums.find(required(albumId), null).orElseThrow();
	}

	/** Replaces the album's genres; exactly one must be primary. */
	List<AdminCatalogRepository.AlbumGenreRow> setGenres(int albumId, AdminRequests.SetGenresRequest request) {
		requireAlbum(albumId);
		requireOnePrimary(request.genres());
		this.transactions.executeWithoutResult((status) -> this.catalog.replaceGenres(albumId,
				genreIds(request.genres()), primaryGenre(request.genres())));
		return this.catalog.genres(albumId);
	}

	/** Adds tracks; a track's own genre is added to the album's genres automatically. */
	List<AlbumRepository.AlbumTrack> addTracks(int albumId, AdminRequests.AddAlbumTracksRequest request) {
		requireAlbum(albumId);
		this.transactions.executeWithoutResult(
				(status) -> request.tracks().forEach((track) -> this.catalog.insertTrack(albumId, track)));
		this.trackSearch.refresh();
		return this.albums.tracks(albumId);
	}

	/** Adds credits; {@code track.composer} of every recording regenerates automatically. */
	SongRepository.SongDetail addCredits(int songId, AdminRequests.AddCreditsRequest request) {
		if (!this.catalog.songExists(songId)) {
			throw ApiException.notFound("No song " + songId + ".");
		}
		this.transactions.executeWithoutResult(
				(status) -> request.credits().forEach((credit) -> this.catalog.insertCredit(songId, credit)));
		return this.songs.find(songId).orElseThrow();
	}

	private void requireAlbum(int albumId) {
		if (!this.albums.exists(albumId)) {
			throw ApiException.notFound("No album " + albumId + ".");
		}
	}

	private static void requireOnePrimary(List<AdminRequests.AlbumGenreInput> genres) {
		long primaries = genres.stream().filter(AdminRequests.AlbumGenreInput::isPrimary).count();
		if (!genres.isEmpty() && primaries != 1) {
			throw ApiException.unprocessable("primary_genre_required",
					"An album with genres has exactly one primary genre (is_primary: true); got " + primaries + ".",
					"genres");
		}
	}

	private static List<Integer> genreIds(List<AdminRequests.AlbumGenreInput> genres) {
		return genres.stream().map(AdminRequests.AlbumGenreInput::genreId).distinct().toList();
	}

	private static @Nullable Integer primaryGenre(List<AdminRequests.AlbumGenreInput> genres) {
		return genres.stream()
			.filter(AdminRequests.AlbumGenreInput::isPrimary)
			.map(AdminRequests.AlbumGenreInput::genreId)
			.findFirst()
			.orElse(null);
	}

	/** Sets the column when its field (same snake_case name) was sent. */
	private static void set(PartialUpdate<?> patch, ColumnChanges changes, String column, @Nullable Object value,
			int sqlType) {
		if (patch.has(column)) {
			changes.set(column, value, sqlType);
		}
	}

	private static @Nullable String upper(@Nullable String countryCode) {
		return (countryCode != null) ? countryCode.trim().toUpperCase(Locale.ROOT) : null;
	}

	private static <T> T required(@Nullable T value) {
		if (value == null) {
			throw new IllegalStateException("Transaction returned no result");
		}
		return value;
	}

}
