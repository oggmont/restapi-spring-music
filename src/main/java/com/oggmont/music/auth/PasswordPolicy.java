package com.oggmont.music.auth;

import com.oggmont.music.common.error.ApiException;
import java.nio.charset.StandardCharsets;

/**
 * Rules for passwords chosen through the API (sign-up, password changes). The
 * configured super user's initial password is set by the operator and isn't checked.
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	/** bcrypt only uses the first 72 bytes, and Spring Security rejects longer input. */
	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	public static void check(String password, String field) {
		if (password.codePointCount(0, password.length()) < MIN_LENGTH || password.isBlank()) {
			throw ApiException.unprocessable("weak_password", "Passwords need at least " + MIN_LENGTH + " characters.",
					field);
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw ApiException.unprocessable("password_too_long",
					"Passwords can be at most " + MAX_BYTES + " bytes long (UTF-8).", field);
		}
	}

}
