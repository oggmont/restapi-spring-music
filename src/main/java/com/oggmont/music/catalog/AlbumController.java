package com.oggmont.music.catalog;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.ItemList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Albums and their track listings. Public; read-only.
 */
@RestController
@RequestMapping("/albums/{id}")
@Tag(name = "Catalog")
class AlbumController {

	private final AlbumRepository albums;

	AlbumController(AlbumRepository albums) {
		this.albums = albums;
	}

	@GetMapping
	@Operation(summary = "Album details")
	AlbumRepository.AlbumDetail get(@PathVariable int id, @CurrentUser @Nullable Caller caller) {
		return this.albums.find(id, (caller != null) ? caller.userId() : null).orElseThrow(() -> noAlbum(id));
	}

	@GetMapping("/tracks")
	@Operation(summary = "Track listing, in disc and track order")
	ItemList<AlbumRepository.AlbumTrack> tracks(@PathVariable int id) {
		if (!this.albums.exists(id)) {
			throw noAlbum(id);
		}
		return ItemList.of(this.albums.tracks(id));
	}

	private static ApiException noAlbum(int id) {
		return ApiException.notFound("No album " + id + ".");
	}

}
