package com.oggmont.music.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Account roles (the {@code auth.role} lookup table). Each implies the ones below it:
 * {@code admin > curator > listener} (see the role hierarchy in {@code SecurityConfig}).
 */
public enum Role {

	/** Regular account: playlists, library and follows. */
	LISTENER,

	/** Can also edit the catalog ({@code /admin/**} except accounts). */
	CURATOR,

	/** Can also manage accounts ({@code /admin/users/**}). */
	ADMIN;

	/** The code stored in the database and used in JSON: {@code "admin"}. */
	@JsonValue
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The Spring Security authority: {@code ROLE_ADMIN}. */
	public String authority() {
		return "ROLE_" + name();
	}

	@JsonCreator
	public static Role fromCode(String code) {
		for (Role role : values()) {
			if (role.code().equals(code)) {
				return role;
			}
		}
		throw new IllegalArgumentException("Unknown role '" + code + "'; expected listener, curator or admin");
	}

}
