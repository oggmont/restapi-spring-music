package com.oggmont.music.library;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's library. {@code PUT} and {@code DELETE} are idempotent: liking twice is
 * fine and still answers {@code 204}.
 */
@RestController
@RequestMapping("/me")
@Tag(name = "Library", description = "Liked tracks and saved albums")
class LibraryController {

	private final LibraryRepository library;

	LibraryController(LibraryRepository library) {
		this.library = library;
	}

	@GetMapping("/tracks")
	@Operation(summary = "Liked tracks, newest first (keyset paging with before and before_id)")
	Page<LibraryRepository.SavedTrack> likedTracks(@CurrentUser Caller caller, @Valid PageParams page,
			@RequestParam(required = false) @Nullable Instant before,
			@RequestParam(name = "before_id", required = false) @Nullable Integer beforeId) {
		return this.library.savedTracks(caller.userId(), page, before, beforeId);
	}

	@PutMapping("/tracks/{trackId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Like a track")
	void like(@CurrentUser Caller caller, @PathVariable int trackId) {
		if (this.library.saveTrack(caller.userId(), trackId) == 0 && !this.library.trackExists(trackId)) {
			throw noTrack(trackId);
		}
	}

	@DeleteMapping("/tracks/{trackId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Unlike a track")
	void unlike(@CurrentUser Caller caller, @PathVariable int trackId) {
		if (this.library.unsaveTrack(caller.userId(), trackId) == 0 && !this.library.trackExists(trackId)) {
			throw noTrack(trackId);
		}
	}

	@GetMapping("/tracks/contains")
	@Operation(summary = "Which of these tracks you liked: ?ids=3674,3668,4199")
	Map<String, Boolean> contains(@CurrentUser Caller caller,
			@RequestParam @Size(min = 1, max = 100) List<@NotNull @Positive Integer> ids) {
		return this.library.containsTracks(caller.userId(), ids);
	}

	@GetMapping("/albums")
	@Operation(summary = "Saved albums, newest first (keyset paging with before and before_id)")
	Page<LibraryRepository.SavedAlbum> savedAlbums(@CurrentUser Caller caller, @Valid PageParams page,
			@RequestParam(required = false) @Nullable Instant before,
			@RequestParam(name = "before_id", required = false) @Nullable Integer beforeId) {
		return this.library.savedAlbums(caller.userId(), page, before, beforeId);
	}

	@PutMapping("/albums/{albumId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Save an album")
	void saveAlbum(@CurrentUser Caller caller, @PathVariable int albumId) {
		if (this.library.saveAlbum(caller.userId(), albumId) == 0 && !this.library.albumExists(albumId)) {
			throw noAlbum(albumId);
		}
	}

	@DeleteMapping("/albums/{albumId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Remove a saved album")
	void removeAlbum(@CurrentUser Caller caller, @PathVariable int albumId) {
		if (this.library.unsaveAlbum(caller.userId(), albumId) == 0 && !this.library.albumExists(albumId)) {
			throw noAlbum(albumId);
		}
	}

	private static ApiException noTrack(int trackId) {
		return ApiException.notFound("No track " + trackId + ".");
	}

	private static ApiException noAlbum(int albumId) {
		return ApiException.notFound("No album " + albumId + ".");
	}

}
