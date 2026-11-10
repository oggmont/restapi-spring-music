package com.oggmont.music.follow;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.ItemList;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.user.UserCard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Follows. {@code PUT} follows and {@code DELETE} unfollows; both are idempotent and
 * answer {@code 204}, except playlist follows (the UI's favorites), which answer
 * {@code 200} with the playlist's new state.
 */
@RestController
@Tag(name = "Follows", description = "Follow playlists, artists and users")
class FollowController {

	private final FollowRepository follows;

	private final FavoriteService favorites;

	FollowController(FollowRepository follows, FavoriteService favorites) {
		this.follows = follows;
		this.favorites = favorites;
	}

	@PutMapping("/me/following/playlists/{id}")
	@Operation(summary = "Favorite (follow) a playlist: at most 5; makes it public",
			description = "Your own playlists (even private ones) and public ones. Idempotent. With 5 favorites "
					+ "already, answers 422 `favorite_limit_reached` unless the body names one to swap out: "
					+ "`{\"replaces\": 25}` (one transaction). 404 when you can't view the playlist, or when "
					+ "`replaces` isn't one of your favorites.")
	FavoriteState followPlaylist(@CurrentUser Caller caller, @PathVariable int id,
			@Valid @RequestBody(required = false) @Nullable FavoriteRequest request) {
		return this.favorites.favorite(caller.userId(), id, (request != null) ? request.replaces() : null);
	}

	@DeleteMapping("/me/following/playlists/{id}")
	@Operation(summary = "Unfavorite (unfollow) a playlist; its last favorite going makes it private",
			description = "Idempotent, and works even if you can no longer view the playlist. The answer says "
					+ "whether it became private.")
	FavoriteState unfollowPlaylist(@CurrentUser Caller caller, @PathVariable int id) {
		return this.favorites.unfavorite(caller.userId(), id);
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
	@Operation(summary = "Your favorites (followed playlists, at most 5): newest first, or A-Z with sort=name")
	Page<FollowRepository.FollowedPlaylist> followedPlaylists(@CurrentUser Caller caller,
			@RequestParam(defaultValue = "followed_at") String sort, @Valid PageParams page) {
		return this.follows.followedPlaylists(caller.userId(), sort(sort, "followed_at"), page);
	}

	@GetMapping("/users/{username}/favorites")
	@Tag(name = "Users")
	@Operation(summary = "A user's favorite playlists (at most 5): oldest first, or A-Z with sort=name",
			description = "Favorites are always public, because a favorite keeps its playlist public.")
	ItemList<FavoritePlaylist> favoritesOf(@PathVariable String username,
			@RequestParam(defaultValue = "favorited_at") String sort) {
		return ItemList.of(this.follows.favoritesOf(userId(username), sort(sort, "favorited_at")));
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
		return FavoriteService.noPlaylist(id);
	}

	/** {@code name}, or the given name for the order the favorites were added in. */
	private static FollowRepository.FavoriteSort sort(String sort, String byDate) {
		String value = sort.trim().toLowerCase(Locale.ROOT);
		if (value.equals("name")) {
			return FollowRepository.FavoriteSort.NAME;
		}
		if (value.equals(byDate)) {
			return FollowRepository.FavoriteSort.FOLLOWED_AT;
		}
		throw ApiException.unprocessable("invalid_sort", "sort must be name or " + byDate + ".", "sort");
	}

	/**
	 * Optional body of {@code PUT /me/following/playlists/{id}}.
	 *
	 * @param replaces a favorite to remove in the same transaction (for a sixth favorite)
	 */
	record FavoriteRequest(@Nullable @Positive Integer replaces) {
	}

}
