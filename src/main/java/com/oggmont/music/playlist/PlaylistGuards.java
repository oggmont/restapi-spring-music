package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.common.error.ApiException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Playlist access rules from API-Design.md:
 * <ul>
 * <li>a playlist the caller can't view is answered with 404, not 403, so private
 * playlists don't reveal that they exist;</li>
 * <li>details (name, visibility...) are changed by the owner only;</li>
 * <li>the track list is changed by the owner and editors.</li>
 * </ul>
 */
@Component
class PlaylistGuards {

	private final PlaylistRepository playlists;

	PlaylistGuards(PlaylistRepository playlists) {
		this.playlists = playlists;
	}

	PlaylistRepository.PlaylistAccess viewable(int playlistId, @Nullable Caller caller, boolean lock) {
		return this.playlists.access(playlistId, idOf(caller), lock)
			.filter(PlaylistRepository.PlaylistAccess::canView)
			.orElseThrow(() -> notFound(playlistId));
	}

	PlaylistRepository.PlaylistAccess owned(int playlistId, Caller caller, boolean lock) {
		PlaylistRepository.PlaylistAccess access = viewable(playlistId, caller, lock);
		if (!access.isOwner()) {
			throw ApiException.forbidden("Only the playlist's owner can do this.");
		}
		return access;
	}

	PlaylistRepository.PlaylistAccess editable(int playlistId, Caller caller, boolean lock) {
		PlaylistRepository.PlaylistAccess access = viewable(playlistId, caller, lock);
		if (!access.canEdit()) {
			throw ApiException.forbidden("Only the owner and editors can change this playlist's tracks.");
		}
		return access;
	}

	static @Nullable Integer idOf(@Nullable Caller caller) {
		return (caller != null) ? caller.userId() : null;
	}

	static ApiException notFound(int playlistId) {
		return ApiException.notFound("No playlist " + playlistId + ".");
	}

	static UUID parseShareToken(String shareToken) {
		try {
			return UUID.fromString(shareToken);
		}
		catch (IllegalArgumentException ex) {
			throw shareLinkNotFound();
		}
	}

	static ApiException shareLinkNotFound() {
		return ApiException.notFound("No playlist has that share link.");
	}

}
