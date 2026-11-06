package com.oggmont.music.catalog;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.ItemList;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Artists, their discographies and line-ups. Public; read-only.
 */
@RestController
@RequestMapping("/artists")
@Tag(name = "Catalog", description = "Artists, albums, tracks, songs, people, genres and labels")
class ArtistController {

	private final ArtistRepository artists;

	private final AlbumRepository albums;

	ArtistController(ArtistRepository artists, AlbumRepository albums) {
		this.artists = artists;
		this.albums = albums;
	}

	@GetMapping
	@Operation(summary = "List artists: ?type=group&country=GB&genre=rock&sort=name|followers")
	Page<ArtistCard> list(@Valid PageParams page, @RequestParam(required = false) @Nullable String type,
			@RequestParam(required = false) @Nullable @Pattern(regexp = "^[A-Za-z]{2}$") String country,
			@RequestParam(required = false) @Nullable String genre,
			@RequestParam(required = false, defaultValue = "name") @Pattern(regexp = "^(name|followers)$",
					message = "must be name or followers") String sort) {
		ArtistRepository.Filter filter = new ArtistRepository.Filter(blankToNull(type),
				(country != null) ? country.toUpperCase(Locale.ROOT) : null, blankToNull(genre),
				ArtistRepository.Sort.valueOf(sort.toUpperCase(Locale.ROOT)));
		return this.artists.list(filter, page);
	}

	@GetMapping("/{slug}")
	@Operation(summary = "Artist page")
	ArtistRepository.ArtistDetail get(@PathVariable String slug, @CurrentUser @Nullable Caller caller) {
		return this.artists.find(slug, (caller != null) ? caller.userId() : null).orElseThrow(() -> noArtist(slug));
	}

	@GetMapping("/{slug}/albums")
	@Operation(summary = "Discography, including collaborations: ?type=studio|live|compilation|soundtrack")
	Page<AlbumCard> albums(@PathVariable String slug, @Valid PageParams page,
			@RequestParam(required = false) @Nullable String type) {
		return this.artists.albums(artistId(slug), blankToNull(type), page);
	}

	@GetMapping("/{slug}/members")
	@Operation(summary = "Band line-up, one entry per stint (a solo artist answers with its person)")
	ItemList<ArtistRepository.Member> members(@PathVariable String slug) {
		return ItemList.of(this.artists.members(artistId(slug)));
	}

	@GetMapping("/{slug}/albums/{albumSlug}")
	@Operation(summary = "Album by URL: /artists/the-beatles/albums/abbey-road")
	AlbumRepository.AlbumDetail album(@PathVariable String slug, @PathVariable String albumSlug,
			@CurrentUser @Nullable Caller caller) {
		int albumId = this.albums.idBySlugs(slug, albumSlug)
			.orElseThrow(() -> ApiException.notFound("No album '" + albumSlug + "' by '" + slug + "'."));
		return this.albums.find(albumId, (caller != null) ? caller.userId() : null).orElseThrow();
	}

	private int artistId(String slug) {
		return this.artists.idBySlug(slug).orElseThrow(() -> noArtist(slug));
	}

	private static ApiException noArtist(String slug) {
		return ApiException.notFound("No artist '" + slug + "'.");
	}

	private static @Nullable String blankToNull(@Nullable String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
