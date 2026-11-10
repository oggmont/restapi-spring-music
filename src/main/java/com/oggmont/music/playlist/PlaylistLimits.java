package com.oggmont.music.playlist;

import com.oggmont.music.common.error.ApiError;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.error.DatabaseErrors;
import com.oggmont.music.common.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * The web UI's limits on playlists (API-Design.md, "Limits"). The database enforces them
 * with triggers ({@code playlist_track_limit}, {@code playlist_owner_limit}); the API
 * checks first only to say how many still fit.
 */
final class PlaylistLimits {

	/** Entries per playlist. */
	static final int MAX_TRACKS = 100;

	/** Playlists a user owns (collaborations don't count). */
	static final int MAX_OWNED = 20;

	private PlaylistLimits() {
	}

	/** {@code 422 playlist_full}: {@code adding} more entries don't fit in a playlist that has {@code count}. */
	static ApiException playlistFull(String name, long count, int adding) {
		long fit = Math.max(0, MAX_TRACKS - count);
		String message;
		if (count > MAX_TRACKS) {
			message = name + " has " + count + " tracks, more than the " + MAX_TRACKS
					+ " a playlist holds now; nothing more can be added.";
		}
		else if (fit == 0) {
			message = name + " has " + MAX_TRACKS + " of " + MAX_TRACKS + " tracks; it's full.";
		}
		else {
			message = name + " has " + count + " of " + MAX_TRACKS + " tracks; " + fit + " more fit"
					+ ((adding > fit) ? ", not " + adding : "") + ".";
		}
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.PLAYLIST_FULL, message,
				List.of(new ApiError.FieldDetail("track_ids", "at most " + fit)));
	}

	/** {@code 422 playlist_full} for a new playlist (create or copy) with too many tracks. */
	static ApiException tooManyForNewPlaylist(int count) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.PLAYLIST_FULL,
				"A playlist holds at most " + MAX_TRACKS + " tracks; this one would have " + count + ".",
				List.of(new ApiError.FieldDetail("track_ids", "at most " + MAX_TRACKS)));
	}

	/** {@code 422 playlist_limit_reached}. */
	static ApiException ownedLimitReached() {
		return ApiException.unprocessable(ErrorCodes.PLAYLIST_LIMIT_REACHED, "You already own " + MAX_OWNED
				+ " playlists, the most you can have. Delete one to make another.");
	}

	/** {@code 422 visibility_follows_favorites}: {@code visibility} was sent. */
	static ApiException visibilityFollowsFavorites() {
		return ApiException.unprocessable(ErrorCodes.VISIBILITY_FOLLOWS_FAVORITES, DatabaseErrors.VISIBILITY_MESSAGE,
				"visibility");
	}

}
