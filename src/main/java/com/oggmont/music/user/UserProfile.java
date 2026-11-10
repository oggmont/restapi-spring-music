package com.oggmont.music.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.auth.Role;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A user profile. {@code email}, {@code role}, {@code updated_at} and the two counts are
 * only shown to the account's owner ({@code GET /me}); {@code is_followed_by_me} only
 * with a token.
 *
 * @param userId {@code app_user.user_id}
 * @param username URL key; permanent
 * @param displayName name shown to others
 * @param countryCode ISO 3166-1 alpha-2
 * @param bio short description
 * @param avatarUrl profile picture
 * @param isVerified editorial and artist accounts
 * @param followerCount number of followers
 * @param followingCount number of users followed
 * @param createdAt when the account was created
 * @param email the account's email (owner only)
 * @param role the account's role (owner only)
 * @param updatedAt last profile change (owner only)
 * @param isFollowedByMe whether the caller follows this user
 * @param ownedPlaylistCount how many playlists the account owns, of at most 20 (owner only)
 * @param favoriteCount how many favorite playlists the account has, of at most 5 (owner only)
 */
public record UserProfile(int userId, String username, String displayName, @Nullable String countryCode,
		@Nullable String bio, @Nullable String avatarUrl, boolean isVerified, int followerCount, int followingCount,
		Instant createdAt, @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String email,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Role role,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Instant updatedAt,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean isFollowedByMe,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer ownedPlaylistCount,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Integer favoriteCount) {
}
