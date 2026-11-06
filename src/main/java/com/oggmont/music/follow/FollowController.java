package com.oggmont.music.follow;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.user.UserCard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Follows. {@code PUT} follows and {@code DELETE} unfollows; both are idempotent and
 * answer {@code 204}.
 */
@RestController
@Tag(name = "Follows", description = "Follow playlists, artists and users")
class FollowController {

	private final FollowRepository follows;

	FollowController(FollowRepository follows) {
		this.follows = follows;
	}

	@PutMapping("/me/following/playlists/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Follow a playlist (404 when you can't view it)")
	void followPlaylist(@CurrentUser Caller caller, @PathVariable int id) {
		if (!this.follows.canViewPlaylist(id, caller.userId())) {
			throw noPlaylist(id);
		}
		this.follows.followPlaylist(caller.userId(), id);
	}

	@DeleteMapping("/me/following/playlists/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Unfollow a playlist (works even if it became private)")
	void unfollowPlaylist(@CurrentUser Caller caller, @PathVariable int id) {
		this.follows.unfollowPlaylist(caller.userId(), id);
	}

	@PutMapping("/me/following/artists/{slug}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Follow an artist")
	void followArtist(@CurrentUser Caller caller, @PathVariable String slug) {
		this.follows.followArtist(caller.userId(), artistId(slug));
	}

	@DeleteMapping("/me/following/artists/{slug}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Unfollow an artist")
	void unfollowArtist(@CurrentUser Caller caller, @PathVariable String slug) {
		this.follows.unfollowArtist(caller.userId(), artistId(slug));
	}

	@PutMapping("/me/following/users/{username}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Follow a user (422 for yourself)")
	void followUser(@CurrentUser Caller caller, @PathVariable String username) {
		this.follows.followUser(caller.userId(), userId(username));
	}

	@DeleteMapping("/me/following/users/{username}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Unfollow a user")
	void unfollowUser(@CurrentUser Caller caller, @PathVariable String username) {
		this.follows.unfollowUser(caller.userId(), userId(username));
	}

	@GetMapping("/me/following/artists")
	@Operation(summary = "Artists you follow, newest first")
	Page<FollowRepository.FollowedArtist> followedArtists(@CurrentUser Caller caller, @Valid PageParams page) {
		return this.follows.followedArtists(caller.userId(), page);
	}

	@GetMapping("/me/following/playlists")
	@Operation(summary = "Playlists you follow, newest first")
	Page<FollowRepository.FollowedPlaylist> followedPlaylists(@CurrentUser Caller caller, @Valid PageParams page) {
		return this.follows.followedPlaylists(caller.userId(), page);
	}

	@GetMapping("/playlists/{id}/followers")
	@Operation(summary = "Who follows a playlist (same visibility rules as the playlist)")
	Page<UserCard> playlistFollowers(@PathVariable int id, @CurrentUser @Nullable Caller caller,
			@Valid PageParams page) {
		if (!this.follows.canViewPlaylist(id, (caller != null) ? caller.userId() : null)) {
			throw noPlaylist(id);
		}
		return this.follows.playlistFollowers(id, page);
	}

	private int artistId(String slug) {
		return this.follows.artistId(slug).orElseThrow(() -> ApiException.notFound("No artist '" + slug + "'."));
	}

	private int userId(String username) {
		return this.follows.userId(username)
			.orElseThrow(() -> ApiException.notFound("No user named '" + username + "'."));
	}

	private static ApiException noPlaylist(int id) {
		return ApiException.notFound("No playlist " + id + ".");
	}

}
