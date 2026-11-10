package com.oggmont.music.follow;

import com.oggmont.music.common.error.ApiException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Favorites: the UI's name for playlist follows (API-Design.md, "Favorites"). The
 * database enforces the rules: at most 5 per user ({@code playlist_follow_limit}, 422
 * {@code favorite_limit_reached}), and a playlist is public while anyone has it as a
 * favorite (triggers set {@code visibility}).
 */
@Service
class FavoriteService {

	private final FollowRepository follows;

	FavoriteService(FollowRepository follows) {
		this.follows = follows;
	}

	/**
	 * Favorites a playlist the caller can view (their own, even while it is private, or a
	 * public one). Idempotent. With {@code replaces}, that favorite is removed in the same
	 * transaction, so the caller never ends up with 4 or 6.
	 */
	@Transactional
	FavoriteState favorite(int me, int playlistId, @Nullable Integer replaces) {
		if (!this.follows.canViewPlaylist(playlistId, me)) {
			throw noPlaylist(playlistId);
		}
		if (this.follows.isFavorite(me, playlistId)) {
			return state(me, playlistId);
		}
		if (replaces != null && !this.follows.unfollowPlaylist(me, replaces)) {
			throw ApiException.notFound("Playlist " + replaces + " isn't one of your favorites.");
		}
		this.follows.followPlaylist(me, playlistId);
		FavoriteState state = state(me, playlistId);
		if (replaces == null) {
			return state;
		}
		FavoriteState old = state(me, replaces);
		return state.withReplaced(new FavoriteState.Replaced(old.playlistId(), old.visibility(), old.followerCount()));
	}

	/**
	 * Removes a favorite. Idempotent; works even when the caller can no longer view the
	 * playlist. If it was the playlist's last favorite, the answer shows it is private now.
	 */
	@Transactional
	FavoriteState unfavorite(int me, int playlistId) {
		boolean removed = this.follows.unfollowPlaylist(me, playlistId);
		if (!removed && !this.follows.canViewPlaylist(playlistId, me)) {
			throw noPlaylist(playlistId);
		}
		return state(me, playlistId);
	}

	private FavoriteState state(int me, int playlistId) {
		return this.follows.favoriteState(me, playlistId).orElseThrow(() -> noPlaylist(playlistId));
	}

	static ApiException noPlaylist(int id) {
		return ApiException.notFound("No playlist " + id + ".");
	}

}
