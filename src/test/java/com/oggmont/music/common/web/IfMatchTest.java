package com.oggmont.music.common.web;

import com.oggmont.music.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class IfMatchTest {

	@Test
	void absentHeaderPassesChecksButFailsRequire() {
		IfMatch ifMatch = IfMatch.parse(null);
		assertThat(ifMatch.isPresent()).isFalse();
		assertThatNoException().isThrownBy(() -> ifMatch.check(7));
		assertThatExceptionOfType(ApiException.class).isThrownBy(ifMatch::require)
			.satisfies((ex) -> assertThat(ex.status()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED));
		assertThat(IfMatch.parse("  ").isPresent()).isFalse();
	}

	@Test
	void strongTagsMatchTheirVersion() {
		IfMatch ifMatch = IfMatch.parse("\"3\"");
		assertThat(ifMatch.matches(3)).isTrue();
		assertThat(ifMatch.matches(4)).isFalse();
		assertThatExceptionOfType(ApiException.class).isThrownBy(() -> ifMatch.check(4))
			.satisfies((ex) -> assertThat(ex.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED));
	}

	@Test
	void listsWildcardsAndWeakTags() {
		assertThat(IfMatch.parse("\"1\", \"2\"").matches(2)).isTrue();
		assertThat(IfMatch.parse("*").matches(42)).isTrue();
		assertThat(IfMatch.parse("W/\"2\"").matches(2)).as("weak tags never match (strong comparison)").isFalse();
		assertThat(IfMatch.parse("2").matches(2)).as("unquoted is malformed").isFalse();
	}

	@Test
	void etagIsTheQuotedVersion() {
		assertThat(IfMatch.etag(12)).isEqualTo("\"12\"");
	}

}
