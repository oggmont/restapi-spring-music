package com.oggmont.music.playlist;

import com.oggmont.music.catalog.TrackCard;
import com.oggmont.music.user.UserRef;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A recent addition to a playlist the caller follows or collaborates on.
 *
 * @param playlist the playlist
 * @param position the entry's position
 * @param addedAt when it was added
 * @param addedBy who added it
 * @param track the track
 */
public record FeedItem(PlaylistRef playlist, int position, Instant addedAt, @Nullable UserRef addedBy, TrackCard track) {

	/**
	 * @param playlistId {@code playlist.playlist_id}
	 * @param name the playlist's name
	 */
	public record PlaylistRef(int playlistId, String name) {
	}

}
