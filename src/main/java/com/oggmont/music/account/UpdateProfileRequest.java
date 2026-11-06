package com.oggmont.music.account;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code PATCH /me}: send only the fields that change; {@code null} clears
 * {@code bio}, {@code avatar_url} and {@code country_code}. Received as a
 * {@code PartialUpdate}, which knows which fields were sent.
 *
 * @param displayName new display name; can't be cleared
 * @param bio new bio
 * @param avatarUrl new avatar URL
 * @param countryCode new country (ISO 3166-1 alpha-2)
 * @param username rejected: usernames are permanent, because they appear in URLs
 */
record UpdateProfileRequest(
		@Nullable @Size(max = 80) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String displayName,
		@Nullable @Size(max = 2000) String bio,
		@Nullable @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String avatarUrl,
		@Nullable @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String countryCode,
		@Nullable String username) {
}
