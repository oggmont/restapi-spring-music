package com.oggmont.music.auth;

import com.oggmont.music.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class PasswordPolicyTest {

	@Test
	void acceptsReasonablePasswords() {
		assertThatNoException().isThrownBy(() -> PasswordPolicy.check("correct horse", "password"));
		assertThatNoException().isThrownBy(() -> PasswordPolicy.check("ñandú-ñandú", "password"));
	}

	@Test
	void rejectsShortBlankAndOverlongPasswords() {
		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> PasswordPolicy.check("2012", "password"))
			.withMessageContaining("at least 8");
		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> PasswordPolicy.check("        ", "password"));
		// 40 two-byte characters are 80 bytes: more than bcrypt uses.
		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> PasswordPolicy.check("é".repeat(40), "password"))
			.withMessageContaining("72 bytes");
	}

}
