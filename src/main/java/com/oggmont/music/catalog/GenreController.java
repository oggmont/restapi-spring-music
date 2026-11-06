package com.oggmont.music.catalog;

import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Genres and labels. Public; read-only.
 */
@RestController
@Tag(name = "Catalog")
class GenreController {

	private final GenreRepository genres;

	GenreController(GenreRepository genres) {
		this.genres = genres;
	}

	@GetMapping("/genres")
	@Operation(summary = "The genre tree")
	ResponseEntity<List<GenreRepository.GenreNode>> genres() {
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(this.genres.tree());
	}

	@GetMapping("/genres/{id}/albums")
	@Operation(summary = "Albums in a genre and its subgenres (?primary_only=true: by primary genre)")
	Page<AlbumCard> albums(@PathVariable int id, @Valid PageParams page,
			@RequestParam(name = "primary_only", defaultValue = "false") boolean primaryOnly) {
		if (!this.genres.exists(id)) {
			throw ApiException.notFound("No genre " + id + ".");
		}
		return this.genres.albums(id, primaryOnly, page);
	}

	@GetMapping("/labels/{slug}")
	@Operation(summary = "A record label with its albums")
	GenreRepository.LabelDetail label(@PathVariable String slug, @Valid PageParams page) {
		return this.genres.label(slug, page).orElseThrow(() -> ApiException.notFound("No label '" + slug + "'."));
	}

}
