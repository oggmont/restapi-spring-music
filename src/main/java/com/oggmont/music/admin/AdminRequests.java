package com.oggmont.music.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Request bodies of the catalog administration endpoints.
 */
final class AdminRequests {

	static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

	static final String URL = "^https?://\\S+$";

	static final String NOT_BLANK = "(?s).*\\S.*";

	private AdminRequests() {
	}

	/**
	 * Body of {@code POST /admin/artists}. The slug is generated from the name when omitted.
	 */
	record CreateArtistRequest(@NotBlank @Size(max = 120) String name,
			@Nullable @Size(max = 140) @Pattern(regexp = SLUG, message = "must be a slug like the-beatles") String slug,
			@Nullable @Size(max = 20) String artistType, @Nullable @Positive Integer personId,
			@Nullable @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String countryCode,
			@Nullable @Size(max = 60) String city, @Nullable @Min(1000) @Max(2100) Integer formedYear,
			@Nullable @Min(1000) @Max(2100) Integer disbandedYear, @Nullable @Size(max = 10000) String biography,
			@Nullable @Size(max = 500) @Pattern(regexp = URL, message = "must be an http(s) URL") String imageUrl,
			@Nullable UUID musicbrainzId) {
	}

	/**
	 * Body of {@code PATCH /admin/artists/{id}} (received as a {@code PartialUpdate}):
	 * absent fields stay unchanged, {@code null} clears them. The slug doesn't follow name
	 * changes; send {@code slug} to change it ({@code null} regenerates it from the name).
	 */
	record UpdateArtistRequest(@Nullable @Size(max = 120) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String name,
			@Nullable @Size(max = 140) @Pattern(regexp = SLUG, message = "must be a slug like the-beatles") String slug,
			@Nullable @Size(max = 20) String artistType, @Nullable @Positive Integer personId,
			@Nullable @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String countryCode,
			@Nullable @Size(max = 60) String city, @Nullable @Min(1000) @Max(2100) Integer formedYear,
			@Nullable @Min(1000) @Max(2100) Integer disbandedYear, @Nullable @Size(max = 10000) String biography,
			@Nullable @Size(max = 500) @Pattern(regexp = URL, message = "must be an http(s) URL") String imageUrl,
			@Nullable UUID musicbrainzId) {
	}

	/**
	 * Body of {@code POST /admin/albums}. The album, its artists and its genres are
	 * inserted in one transaction, because the database checks at commit that an album
	 * lists its credited artist and has exactly one primary genre.
	 */
	record CreateAlbumRequest(@NotBlank @Size(max = 160) String title, @Nullable @Size(max = 80) String edition,
			@NotNull @Positive Integer artistId,
			@Nullable @Size(max = 180) @Pattern(regexp = SLUG, message = "must be a slug like abbey-road") String slug,
			@Nullable @Size(max = 20) String albumType, @Nullable LocalDate releaseDate,
			@Nullable @Min(1000) @Max(2100) Integer releaseYear, @Nullable @Positive Integer labelId,
			@Nullable @Pattern(regexp = "^[0-9]{12,14}$", message = "must be 12 to 14 digits") String upc,
			@Nullable @Size(max = 500) @Pattern(regexp = URL, message = "must be an http(s) URL") String coverUrl,
			@Nullable UUID musicbrainzId, @NotEmpty @Size(max = 50) List<@NotNull @Valid AlbumArtistInput> artists,
			@Nullable @Size(max = 20) List<@NotNull @Valid AlbumGenreInput> genres) {
	}

	/**
	 * @param artistId the artist
	 * @param role main (default), featured, conductor...
	 */
	record AlbumArtistInput(@NotNull @Positive Integer artistId, @Nullable @Size(max = 20) String role) {
	}

	/**
	 * @param genreId the genre
	 * @param isPrimary whether it is the album's primary genre (exactly one)
	 */
	record AlbumGenreInput(@NotNull @Positive Integer genreId, boolean isPrimary) {
	}

	/** Body of {@code PUT /admin/albums/{id}/genres}: the album's complete set of genres. */
	record SetGenresRequest(@NotNull @Size(max = 20) List<@NotNull @Valid AlbumGenreInput> genres) {
	}

	/** Body of {@code POST /admin/albums/{id}/tracks}. */
	record AddAlbumTracksRequest(@NotEmpty @Size(max = 200) List<@NotNull @Valid TrackInput> tracks) {
	}

	/**
	 * @param name the track's name
	 * @param discNumber disc (default 1)
	 * @param trackNumber position on the disc
	 * @param milliseconds running time
	 * @param songId the composition it records
	 * @param genreId its own genre, when it differs from the album's (added to the
	 * album's genres automatically)
	 * @param composer composer text, for songs without credits
	 * @param bytes file size
	 * @param isrc International Standard Recording Code
	 */
	record TrackInput(@NotBlank @Size(max = 200) String name, @Nullable @Positive Integer discNumber,
			@Nullable @Positive Integer trackNumber, @NotNull @Positive Integer milliseconds,
			@Nullable @Positive Integer songId, @Nullable @Positive Integer genreId,
			@Nullable @Size(max = 1000) String composer, @Nullable @PositiveOrZero Integer bytes,
			@Nullable @Pattern(regexp = "^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$", message = "must look like GBAYE0601498") String isrc) {
	}

	/** Body of {@code POST /admin/songs/{id}/credits}. */
	record AddCreditsRequest(@NotEmpty @Size(max = 50) List<@NotNull @Valid CreditInput> credits) {
	}

	/**
	 * @param personId the person
	 * @param role writer, composer, lyricist, arranger, producer...
	 * @param position credit order (default: after the existing credits)
	 * @param creditedAs the name as printed, when it differs from the person's name
	 */
	record CreditInput(@NotNull @Positive Integer personId, @NotBlank @Size(max = 20) String role,
			@Nullable @Positive Integer position, @Nullable @Size(max = 120) String creditedAs) {
	}

}
