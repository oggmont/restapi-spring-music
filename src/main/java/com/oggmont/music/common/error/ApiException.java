package com.oggmont.music.common.error;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * An error with a known HTTP status and error code, rendered as an {@link ApiError}.
 * Throw it from services and controllers; {@link GlobalExceptionHandler} turns it into
 * the response.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final List<ApiError.FieldDetail> details;

	public ApiException(HttpStatus status, String code, String message, List<ApiError.FieldDetail> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = List.copyOf(details);
	}

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of());
	}

	public HttpStatus status() {
		return this.status;
	}

	public String code() {
		return this.code;
	}

	public List<ApiError.FieldDetail> details() {
		return this.details;
	}

	public ApiError toApiError() {
		return ApiError.of(this.code, getMessage(), this.details);
	}

	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, message);
	}

	public static ApiException unauthorized(String code, String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
	}

	public static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, message);
	}

	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message);
	}

	public static ApiException conflict(String code, String message, @Nullable String field) {
		return new ApiException(HttpStatus.CONFLICT, code, message, fieldDetail(field, message));
	}

	public static ApiException unprocessable(String code, String message, @Nullable String field) {
		return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message, fieldDetail(field, message));
	}

	public static ApiException unprocessable(String code, String message) {
		return unprocessable(code, message, null);
	}

	public static ApiException preconditionFailed(String message) {
		return new ApiException(HttpStatus.PRECONDITION_FAILED, ErrorCodes.PRECONDITION_FAILED, message);
	}

	public static ApiException preconditionRequired(String message) {
		return new ApiException(HttpStatus.PRECONDITION_REQUIRED, ErrorCodes.PRECONDITION_REQUIRED, message);
	}

	private static List<ApiError.FieldDetail> fieldDetail(@Nullable String field, String message) {
		return (field != null) ? List.of(new ApiError.FieldDetail(field, message)) : List.of();
	}

}
