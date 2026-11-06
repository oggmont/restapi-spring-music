package com.oggmont.music.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST /users}.
 *
 * @param username permanent URL handle: 3 to 30 lowercase letters, digits, '_' or '.'
 * @param displayName name shown to others
 * @param email the account's email, only ever shown to its owner
 * @param countryCode ISO 3166-1 alpha-2
 * @param password at least 8 characters
 */
record SignUpRequest(
		@NotBlank @Pattern(regexp = "^[a-z0-9_.]{3,30}$",
				message = "must be 3 to 30 lowercase letters, digits, '_' or '.'") String username,
		@NotBlank @Size(max = 80) String displayName, @NotBlank @Email @Size(max = 254) String email,
		@Nullable @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code, e.g. MX") String countryCode,
		@NotBlank @Size(min = 8, max = 72) String password) {
}
