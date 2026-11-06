package com.oggmont.music.auth;

import java.io.Serializable;

/**
 * The authenticated user making a request ("the caller" in API-Design.md), resolved
 * from the access token's {@code sub} claim through {@code app_user.auth_subject}.
 * <p>
 * Inject it with {@link CurrentUser}: {@code @CurrentUser Caller caller}, or
 * {@code @CurrentUser @Nullable Caller caller} on endpoints that also work anonymously.
 *
 * @param userId {@code app_user.user_id}
 * @param username {@code app_user.username}
 * @param role the account's role
 */
public record Caller(int userId, String username, Role role) implements Serializable {
}
