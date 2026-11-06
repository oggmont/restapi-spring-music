package com.oggmont.music.common;

/**
 * URL layout of the API.
 * <p>
 * Every controller is mapped under {@link #PREFIX} (see {@code WebConfig}), and Spring
 * Framework's API versioning reads the version from that path segment, so
 * {@code /api/v1/...} is served and unsupported versions such as {@code /api/v2/...}
 * are rejected with {@code 400}.
 */
public final class ApiPaths {

	/** The only supported API version. */
	public static final String CURRENT_VERSION = "1";

	/** Prefix of every controller mapping; {@code {version}} is resolved by API versioning. */
	public static final String PREFIX = "/api/{version}";

	/** Concrete base path of the current version, used to build {@code Location} headers. */
	public static final String BASE = "/api/v" + CURRENT_VERSION;

	private ApiPaths() {
	}

}
