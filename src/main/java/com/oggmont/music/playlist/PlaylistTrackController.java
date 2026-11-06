package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.common.web.IfMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A playlist's entries. Writes need {@code If-Match: "<version>"} and the owner or editor
 * role; each response carries the new version as its {@code ETag}.
 */
@RestController
@RequestMapping("/playlists")
@Tag(name = "Playlist tracks", description = "Read and edit a playlist's track list")
class PlaylistTrackController {

	private final PlaylistTrackService tracks;

	PlaylistTrackController(PlaylistTrackService tracks) {
		this.tracks = tracks;
	}

	@GetMapping("/{id}/tracks")
	@Operation(summary = "Entries in order (keyset paging with after_position)")
	ResponseEntity<PlaylistTracksPage> list(@PathVariable int id, @CurrentUser @Nullable Caller caller,
			@Valid PageParams page,
			@RequestParam(name = "after_position", required = false) @Nullable @Min(0) Integer afterPosition) {
		PlaylistTracksPage entries = this.tracks.list(id, caller, page, afterPosition);
		return ResponseEntity.ok().eTag(IfMatch.etag(entries.version())).body(entries);
	}

	@GetMapping("/shared/{shareToken}/tracks")
	@Operation(summary = "Entries of a playlist opened by its share link")
	ResponseEntity<PlaylistTracksPage> listShared(@PathVariable String shareToken, @CurrentUser @Nullable Caller caller,
			@Valid PageParams page,
			@RequestParam(name = "after_position", required = false) @Nullable @Min(0) Integer afterPosition) {
		PlaylistTracksPage entries = this.tracks.listShared(shareToken, caller, page, afterPosition);
		return ResponseEntity.ok().eTag(IfMatch.etag(entries.version())).body(entries);
	}

	@PostMapping("/{id}/tracks")
	@Operation(summary = "Add tracks at the end, or at a position (owner, editor)")
	ResponseEntity<TrackListChanges.Added> add(@PathVariable int id, @CurrentUser Caller caller,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody PlaylistRequests.AddTracksRequest request) {
		TrackListChanges.Added added = this.tracks.add(id, caller, request, IfMatch.parse(ifMatch));
		return ResponseEntity.status(HttpStatus.CREATED).eTag(IfMatch.etag(added.version())).body(added);
	}

	@PostMapping("/{id}/tracks/move")
	@Operation(summary = "Move an entry to another position (owner, editor)")
	ResponseEntity<TrackListChanges.Moved> move(@PathVariable int id, @CurrentUser Caller caller,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody PlaylistRequests.MoveTrackRequest request) {
		TrackListChanges.Moved moved = this.tracks.move(id, caller, request, IfMatch.parse(ifMatch));
		return ResponseEntity.ok().eTag(IfMatch.etag(moved.version())).body(moved);
	}

	@DeleteMapping("/{id}/tracks/{position}")
	@Operation(summary = "Remove one entry; the following entries move up (owner, editor)")
	ResponseEntity<TrackListChanges.RemovedOne> removeAt(@PathVariable int id, @PathVariable @Positive int position,
			@CurrentUser Caller caller,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		TrackListChanges.RemovedOne removed = this.tracks.removeAt(id, caller, position, IfMatch.parse(ifMatch));
		return ResponseEntity.ok().eTag(IfMatch.etag(removed.version())).body(removed);
	}

	@DeleteMapping(path = "/{id}/tracks", params = "track_id")
	@Operation(summary = "Remove every occurrence of a track (owner, editor)")
	ResponseEntity<TrackListChanges.RemovedAll> removeTrack(@PathVariable int id,
			@RequestParam(name = "track_id") @Positive int trackId, @CurrentUser Caller caller,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		TrackListChanges.RemovedAll removed = this.tracks.removeTrack(id, caller, trackId, IfMatch.parse(ifMatch));
		return ResponseEntity.ok().eTag(IfMatch.etag(removed.version())).body(removed);
	}

}
