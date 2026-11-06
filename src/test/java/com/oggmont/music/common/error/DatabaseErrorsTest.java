package com.oggmont.music.common.error;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseErrorsTest {

	@Test
	void knownConstraintsNameTheCodeAndField() {
		ApiException ex = DatabaseErrors.translate(violation("23505", "app_user_username_key", "duplicate key")).orElseThrow();
		assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ex.code()).isEqualTo("username_taken");
		assertThat(ex.details()).singleElement().satisfies((d) -> assertThat(d.field()).isEqualTo("username"));

		ApiException fk = DatabaseErrors.translate(violation("23503", "playlist_track_track_id_fkey", "fk")).orElseThrow();
		assertThat(fk.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(fk.code()).isEqualTo("unknown_track");
	}

	@Test
	void unknownConstraintsFallBackOnTheSqlState() {
		assertThat(DatabaseErrors.translate(violation("23505", "some_other_key", "dup")).orElseThrow().status())
			.isEqualTo(HttpStatus.CONFLICT);
		assertThat(DatabaseErrors.translate(violation("23514", "some_check", "check")).orElseThrow().code())
			.isEqualTo("constraint_violation");
	}

	@Test
	void triggerMessagesArePassedThrough() {
		ApiException privilege = DatabaseErrors
			.translate(violation("42501", null, "user 7 can't add tracks to playlist 20: only its owner and editors can"))
			.orElseThrow();
		assertThat(privilege.status()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(privilege.getMessage()).isEqualTo("user 7 can't add tracks to playlist 20: only its owner and editors can");

		ApiException integrity = DatabaseErrors
			.translate(violation("23000", null, "user 2 owns playlist 20 and can't also be a collaborator"))
			.orElseThrow();
		assertThat(integrity.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(integrity.code()).isEqualTo("integrity_violation");
	}

	@Test
	void otherErrorsAreNotTheClientsFault() {
		assertThat(DatabaseErrors.translate(violation("40001", null, "could not serialize"))).isEmpty();
		assertThat(DatabaseErrors.translate(new IllegalStateException("no database involved"))).isEmpty();
	}

	@Test
	void recognizesAViolationOfANamedConstraint() {
		DataIntegrityViolationException ex = violation("23503", "user_saved_track_track_id_fkey", "fk");
		assertThat(DatabaseErrors.isViolation(ex, "user_saved_track_track_id_fkey")).isTrue();
		assertThat(DatabaseErrors.isViolation(ex, "another_constraint")).isFalse();
	}

	/** What Spring throws for a PostgreSQL error: the driver exception as cause. */
	private static DataIntegrityViolationException violation(String sqlState, String constraint, String message) {
		StringBuilder fields = new StringBuilder().append("SERROR\0").append("C").append(sqlState).append('\0')
			.append("M").append(message).append('\0');
		if (constraint != null) {
			fields.append("n").append(constraint).append('\0');
		}
		return new DataIntegrityViolationException("statement failed",
				new PSQLException(new ServerErrorMessage(fields.toString())));
	}

}
