package com.oggmont.music.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * The super user created at startup ({@code app.admin.*}, from {@code APP_ADMIN_*}).
 *
 * @param username username of the super user; nothing is created when blank
 * @param password initial password; only used when the account has no password yet
 * @param email email of a newly created account (default {@code <username>@localhost})
 * @param displayName display name of a newly created account (default: the username)
 */
@ConfigurationProperties("app.admin")
public record AdminProperties(@Nullable String username, @Nullable String password, @Nullable String email,
		@Nullable String displayName) {

	public boolean isConfigured() {
		return StringUtils.hasText(this.username) && StringUtils.hasText(this.password);
	}

}
