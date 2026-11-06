package com.oggmont.music.playlist;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.user.UserRef;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A playlist as it appears in lists, from the {@code playlist_summary} view.
 *
 * @param playlistId {@code playlist.playlist_id}
 * @param name the playlist's name
 * @param description what it's about
 * @param visibility public, unlisted or private
 * @param coverUrl cover image
 * @param owner who owns it
 * @param isCollaborative whether it has editors
 * @param trackCount number of entries
 * @param durationMs total running time
 * @param followerCount number of followers
 * @param version changes whenever the track list changes (the ETag)
 * @param createdAt when it was created
 * @param updatedAt last change
 * @param myRole the caller's role: owner, editor or viewer ({@code /me/playlists} only)
 */
public record PlaylistSummary(int playlistId, String name, @Nullable String description, String visibility,
		@Nullable String coverUrl, UserRef owner, boolean isCollaborative, int trackCount, long durationMs,
		int followerCount, int version, Instant createdAt, Instant updatedAt,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String myRole) {
}
