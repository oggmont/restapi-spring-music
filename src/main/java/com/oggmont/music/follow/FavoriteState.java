package com.oggmont.music.follow;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * Answer of {@code PUT} and {@code DELETE /me/following/playlists/{id}}: the playlist's
 * state after the change, so the client can update the heart, the visibility badge and
 * the counts without another request.
 *
 * @param playlistId the playlist
 * @param isFavorite whether the caller now has it as a favorite
 * @param visibility public while anyone has it as a favorite, otherwise private
 * @param followerCount how many users have it as a favorite
 * @param myFavoriteCount how many favorites the caller has now (at most 5)
 * @param replaced the favorite given as {@code replaces}, after it was removed
 */
record FavoriteState(int playlistId, boolean isFavorite, String visibility, int followerCount, int myFavoriteCount,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Replaced replaced) {

	FavoriteState withReplaced(Replaced replaced) {
		return new FavoriteState(this.playlistId, this.isFavorite, this.visibility, this.followerCount,
				this.myFavoriteCount, replaced);
	}

	/**
	 * @param playlistId the playlist that is no longer a favorite
	 * @param visibility its visibility now (private if that was its last favorite)
	 * @param followerCount how many users still have it as a favorite
	 */
	record Replaced(int playlistId, String visibility, int followerCount) {
	}

}
