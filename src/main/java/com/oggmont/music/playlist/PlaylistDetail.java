package com.oggmont.music.playlist;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.user.UserRef;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A playlist's details ({@code GET /playlists/{id}}).
 *
 * @param playlistId {@code playlist.playlist_id}
 * @param name the playlist's name
 * @param description what it's about
 * @param visibility public, unlisted or private
 * @param shareToken secret for the share link; only ever sent to the owner
 * @param coverUrl cover image
 * @param owner who owns it
 * @param isCollaborative whether it has editors
 * @param collaboratorCount number of editors and viewers
 * @param trackCount number of entries
 * @param durationMs total running time
 * @param followerCount number of followers
 * @param version changes whenever the track list changes; also sent as the ETag
 * @param myAccess what the caller can do; only sent with a token
 * @param createdAt when it was created
 * @param updatedAt last change
 */
public record PlaylistDetail(int playlistId, String name, @Nullable String description, String visibility,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable UUID shareToken, @Nullable String coverUrl, UserRef owner,
		boolean isCollaborative, int collaboratorCount, int trackCount, long durationMs, int followerCount, int version,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable MyAccess myAccess, Instant createdAt, Instant updatedAt) {

	/**
	 * @param canView always true when the caller got the playlist at all
	 * @param canEdit whether the caller may change the track list (owner or editor)
	 * @param isFollowing whether the caller follows the playlist
	 */
	public record MyAccess(boolean canView, boolean canEdit, boolean isFollowing) {
	}

}
