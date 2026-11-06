package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An album as it appears in lists, from the {@code album_summary} view.
 *
 * @param albumId {@code album.album_id}
 * @param title the album's title
 * @param edition e.g. {@code "Deluxe"}, to tell same-title releases apart
 * @param slug URL key, unique per credited artist
 * @param releaseDate exact release date, when known
 * @param releaseYear release year, when known
 * @param albumType studio, live, compilation...
 * @param artist the artist as credited on the cover
 * @param primaryGenre the album's primary genre
 * @param genres all its genres, primary first
 * @param trackCount number of tracks
 * @param discCount number of discs
 * @param durationMs total running time
 * @param coverUrl cover image
 * @param role the artist's role on the album, in an artist's discography
 */
public record AlbumCard(int albumId, String title, @Nullable String edition, String slug, @Nullable LocalDate releaseDate,
		@Nullable Integer releaseYear, @Nullable String albumType, ArtistRef artist, @Nullable String primaryGenre,
		List<String> genres, int trackCount, int discCount, long durationMs, @Nullable String coverUrl,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String role) {

	public AlbumCard withRole(String role) {
		return new AlbumCard(this.albumId, this.title, this.edition, this.slug, this.releaseDate, this.releaseYear,
				this.albumType, this.artist, this.primaryGenre, this.genres, this.trackCount, this.discCount,
				this.durationMs, this.coverUrl, role);
	}

}
