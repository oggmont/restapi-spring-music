package com.oggmont.music.playlist;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A user who can edit or see someone else's playlist.
 *
 * @param userId {@code app_user.user_id}
 * @param username URL key
 * @param displayName name shown to others
 * @param avatarUrl profile picture
 * @param role editor or viewer
 * @param addedAt when they were added
 */
public record Collaborator(int userId, String username, String displayName, @Nullable String avatarUrl, String role,
		Instant addedAt) {
}
