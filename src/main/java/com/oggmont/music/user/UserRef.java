package com.oggmont.music.user;

/**
 * A link to a user profile: {@code /users/{username}}.
 *
 * @param userId {@code app_user.user_id}
 * @param username URL key
 * @param displayName name shown to others
 */
public record UserRef(int userId, String username, String displayName) {
}
