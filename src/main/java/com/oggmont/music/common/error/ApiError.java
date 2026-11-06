package com.oggmont.music.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Error body, as defined in API-Design.md:
 * <pre>{@code { "error": { "code": "forbidden", "message": "..." } }}</pre>
 * Validation errors also list the offending fields in {@code details}.
 *
 * @param error the error
 */
public record ApiError(Body error) {

	public static ApiError of(String code, String message, List<FieldDetail> details) {
		return new ApiError(new Body(code, message, List.copyOf(details)));
	}

	public static ApiError of(String code, String message) {
		return of(code, message, List.of());
	}

	/**
	 * @param code stable, machine-readable code (snake_case), e.g. {@code username_taken}
	 * @param message human-readable explanation
	 * @param details the fields to blame, when the error is about the request's content
	 */
	public record Body(String code, String message, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldDetail> details) {
	}

	/**
	 * @param field the request field (snake_case, as sent), query parameter or path variable
	 * @param message what is wrong with it
	 */
	public record FieldDetail(String field, String message) {
	}

}
