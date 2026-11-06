package com.oggmont.music.common.web;

import com.oggmont.music.common.error.ApiException;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The {@code If-Match} request header, used for optimistic locking on playlists: the
 * client sends the {@code ETag} (the playlist version) it loaded, and a write only goes
 * ahead if the version is still current.
 * <p>
 * Comparison is strong (RFC 9110): weak tags ({@code W/"1"}) never match, and
 * {@code *} matches any version.
 */
public final class IfMatch {

	private static final IfMatch ABSENT = new IfMatch(false, false, Set.of());

	private final boolean present;

	private final boolean any;

	private final Set<String> tags;

	private IfMatch(boolean present, boolean any, Set<String> tags) {
		this.present = present;
		this.any = any;
		this.tags = tags;
	}

	public static IfMatch parse(@Nullable String header) {
		if (header == null || header.isBlank()) {
			return ABSENT;
		}
		Set<String> tags = new HashSet<>();
		boolean any = false;
		for (String part : header.split(",")) {
			String tag = part.trim();
			if (tag.equals("*")) {
				any = true;
			}
			else if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
				tags.add(tag.substring(1, tag.length() - 1));
			}
			// weak or malformed tags can never match a strong comparison
		}
		return new IfMatch(true, any, Set.copyOf(tags));
	}

	/** The ETag of a playlist version: {@code "3"}. */
	public static String etag(int version) {
		return "\"" + version + "\"";
	}

	public boolean isPresent() {
		return this.present;
	}

	public boolean matches(int version) {
		return this.any || this.tags.contains(Integer.toString(version));
	}

	/** Answers 428 when the header is missing: required for track list changes. */
	public IfMatch require() {
		if (!this.present) {
			throw ApiException.preconditionRequired(
					"Send If-Match with the playlist version you loaded (its ETag), e.g. If-Match: \"1\".");
		}
		return this;
	}

	/** Answers 412 when the header is present and doesn't match the current version. */
	public void check(int currentVersion) {
		if (this.present && !matches(currentVersion)) {
			throw ApiException.preconditionFailed("The playlist has changed since you loaded it (it is now at version "
					+ currentVersion + "). Reload it and try again.");
		}
	}

}
