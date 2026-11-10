package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.ApiPaths;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.common.paging.Sample;
import com.oggmont.music.common.web.IfMatch;
import com.oggmont.music.common.web.PartialUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playlists. {@code playlist.version} is sent as the {@code ETag}; writes may send it
 * back in {@code If-Match} and get {@code 412} if someone else changed the playlist.
 */
@RestController
@Tag(name = "Playlists", description = "Create, share, copy and edit playlists")
class PlaylistController {

	private final PlaylistService playlists;

	PlaylistController(PlaylistService playlists) {
		this.playlists = playlists;
	}

	@PostMapping("/playlists")
	@Operation(summary = "Create a playlist, optionally with up to 100 tracks (always private; at most 20 per user)",
			description = "New playlists are private: visibility follows favorites, so sending `visibility` answers "
					+ "422 `visibility_follows_favorites`. 422 `playlist_limit_reached` when you already own 20, "
					+ "422 `playlist_full` for more than 100 `track_ids`.")
	ResponseEntity<PlaylistDetail> create(@CurrentUser Caller caller, @Valid @RequestBody PlaylistRequests.CreatePlaylistRequest request) {
		PlaylistDetail playlist = this.playlists.create(caller, request);
		return ResponseEntity.created(location(playlist)).eTag(IfMatch.etag(playlist.version())).body(playlist);
	}

	@GetMapping("/playlists/{id}")
	@Operation(summary = "Playlist details (404 when you can't view it)")
	ResponseEntity<PlaylistDetail> get(@PathVariable int id, @CurrentUser @Nullable Caller caller) {
		return withEtag(this.playlists.get(id, caller));
	}

	@PatchMapping("/playlists/{id}")
	@Operation(summary = "Rename, describe or change the cover (owner only)",
			description = "Visibility can't be changed here: it follows favorites (422 `visibility_follows_favorites`).")
	ResponseEntity<PlaylistDetail> update(@PathVariable int id, @CurrentUser Caller caller,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody PartialUpdate<PlaylistRequests.UpdatePlaylistRequest> request) {
		return withEtag(this.playlists.update(id, caller, request, IfMatch.parse(ifMatch)));
	}

	@DeleteMapping("/playlists/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete a playlist (owner only)")
	void delete(@PathVariable int id, @CurrentUser Caller caller,
			@RequestHeader(name = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		this.playlists.delete(id, caller, IfMatch.parse(ifMatch));
	}

	@GetMapping("/playlists/shared/{shareToken}")
	@Operation(summary = "Open a playlist by its share link")
	ResponseEntity<PlaylistDetail> getShared(@PathVariable String shareToken, @CurrentUser @Nullable Caller caller) {
		return withEtag(this.playlists.getShared(shareToken, caller));
	}

	@PostMapping("/playlists/{id}/share-token")
	@Operation(summary = "Make a new share link; the old one stops working (owner only)")
	Map<String, UUID> newShareToken(@PathVariable int id, @CurrentUser Caller caller) {
		return Map.of("share_token", this.playlists.newShareToken(id, caller));
	}

	@PostMapping("/playlists/{id}/copy")
	@Operation(summary = "Copy a playlist into your account (as private; counts toward your 20)")
	ResponseEntity<PlaylistDetail> copy(@PathVariable int id, @CurrentUser Caller caller,
			@Valid @RequestBody(required = false) PlaylistRequests.@Nullable CopyPlaylistRequest request) {
		PlaylistDetail copy = this.playlists.copy(id, caller, (request != null) ? request.name() : null);
		return ResponseEntity.created(location(copy)).eTag(IfMatch.etag(copy.version())).body(copy);
	}

	@GetMapping("/playlists/random")
	@Operation(summary = "Random public playlists by other users that you haven't favorited",
			description = "Each call is a new sample. Send the IDs already on screen as `exclude`. Without a token, "
					+ "any public playlists.")
	Sample<PlaylistSummary> random(@CurrentUser @Nullable Caller caller,
			@RequestParam(defaultValue = "12") @Min(1) @Max(100) int limit,
			@RequestParam(required = false) @Nullable @Size(max = 1000) List<Integer> exclude) {
		return new Sample<>(this.playlists.random(caller, limit, (exclude != null) ? exclude : List.of()));
	}

	@GetMapping("/me/playlists")
	@Operation(summary = "Playlists you own or collaborate on, with your role, favorite flag and when you last added",
			description = "`role` filters by your role (the add menu uses `owner`). `sort`: `created` (default, "
					+ "newest first), `name` (A-Z, ignoring accents) or `last_added` (the playlist you added to most "
					+ "recently first). `contains_track` adds `contains_track` to each item.")
	Page<MyPlaylist> mine(@CurrentUser Caller caller, @RequestParam(required = false) @Nullable String role,
			@RequestParam(defaultValue = "created") String sort,
			@RequestParam(name = "contains_track", required = false) @Nullable @Positive Integer containsTrack,
			@Valid PageParams page) {
		return this.playlists.mine(caller, role(role), memberSort(sort), containsTrack, page);
	}

	@GetMapping("/me/feed")
	@Operation(summary = "Recent additions to playlists you follow or collaborate on")
	Page<FeedItem> feed(@CurrentUser Caller caller, @Valid PageParams page) {
		return this.playlists.feed(caller, page);
	}

	@GetMapping("/users/{username}/playlists")
	@Tag(name = "Users")
	@Operation(summary = "A user's public playlists")
	Page<PlaylistSummary> publicPlaylists(@PathVariable String username, @Valid PageParams page) {
		return this.playlists.publicPlaylistsOf(username, page);
	}

	private static @Nullable String role(@Nullable String role) {
		if (role == null || role.isBlank()) {
			return null;
		}
		String code = role.trim().toLowerCase(Locale.ROOT);
		if (!List.of("owner", "editor", "viewer").contains(code)) {
			throw ApiException.unprocessable("invalid_role", "role must be owner, editor or viewer.", "role");
		}
		return code;
	}

	private static PlaylistRepository.MemberSort memberSort(String sort) {
		return switch (sort.trim().toLowerCase(Locale.ROOT)) {
			case "name" -> PlaylistRepository.MemberSort.NAME;
			case "last_added" -> PlaylistRepository.MemberSort.LAST_ADDED;
			case "created" -> PlaylistRepository.MemberSort.CREATED;
			default -> throw ApiException.unprocessable("invalid_sort", "sort must be name, last_added or created.",
					"sort");
		};
	}

	private static URI location(PlaylistDetail playlist) {
		return URI.create(ApiPaths.BASE + "/playlists/" + playlist.playlistId());
	}

	private static ResponseEntity<PlaylistDetail> withEtag(PlaylistDetail playlist) {
		return ResponseEntity.ok().eTag(IfMatch.etag(playlist.version())).body(playlist);
	}

}
