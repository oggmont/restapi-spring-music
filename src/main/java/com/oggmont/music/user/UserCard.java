package com.oggmont.music.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A user as it appears in follower lists.
 *
 * @param userId {@code app_user.user_id}
 * @param username URL key
 * @param displayName name shown to others
 * @param avatarUrl profile picture
 * @param isVerified editorial and artist accounts
 * @param followerCount number of followers
 * @param followedAt when the follow happened, in follower lists
 */
public record UserCard(int userId, String username, String displayName, @Nullable String avatarUrl, boolean isVerified,
		int followerCount, @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Instant followedAt) {
}
