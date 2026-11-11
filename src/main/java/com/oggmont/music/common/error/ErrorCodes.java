package com.oggmont.music.common.error;

import org.springframework.http.HttpStatusCode;

/**
 * Generic error codes. More specific ones (such as {@code username_taken}) are defined
 * where they are raised, mostly in {@link DatabaseErrors}.
 */
public final class ErrorCodes {

	public static final String BAD_REQUEST = "bad_request";

	public static final String UNAUTHORIZED = "unauthorized";

	public static final String INVALID_TOKEN = "invalid_token";

	public static final String FORBIDDEN = "forbidden";

	public static final String NOT_FOUND = "not_found";

	public static final String METHOD_NOT_ALLOWED = "method_not_allowed";

	public static final String NOT_ACCEPTABLE = "not_acceptable";

	public static final String CONFLICT = "conflict";

	public static final String PRECONDITION_FAILED = "precondition_failed";

	public static final String PAYLOAD_TOO_LARGE = "payload_too_large";

	public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";

	public static final String VALIDATION_FAILED = "validation_failed";

	public static final String PRECONDITION_REQUIRED = "precondition_required";

	public static final String TOO_MANY_REQUESTS = "too_many_requests";

	public static final String INTERNAL_ERROR = "internal_error";

	/** A sixth favorite playlist (limit 5). */
	public static final String FAVORITE_LIMIT_REACHED = "favorite_limit_reached";

	/** A 21st track in a playlist (limit 20). */
	public static final String PLAYLIST_FULL = "playlist_full";

	/** A 21st owned playlist (limit 20). */
	public static final String PLAYLIST_LIMIT_REACHED = "playlist_limit_reached";

	/** {@code visibility} sent to {@code POST} or {@code PATCH /playlists}: it follows favorites. */
	public static final String VISIBILITY_FOLLOWS_FAVORITES = "visibility_follows_favorites";

	public static final String SERVICE_UNAVAILABLE = "service_unavailable";

	private ErrorCodes() {
	}

	/** The generic code for a status, used when nothing more specific is known. */
	public static String forStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> BAD_REQUEST;
			case 401 -> UNAUTHORIZED;
			case 403 -> FORBIDDEN;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 406 -> NOT_ACCEPTABLE;
			case 409 -> CONFLICT;
			case 412 -> PRECONDITION_FAILED;
			case 413 -> PAYLOAD_TOO_LARGE;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			case 422 -> VALIDATION_FAILED;
			case 428 -> PRECONDITION_REQUIRED;
			case 429 -> TOO_MANY_REQUESTS;
			case 503 -> SERVICE_UNAVAILABLE;
			default -> status.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST;
		};
	}

}
