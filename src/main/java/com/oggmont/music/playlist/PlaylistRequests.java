package com.oggmont.music.playlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Request bodies of the playlist endpoints.
 */
final class PlaylistRequests {

	static final int MAX_TRACKS_PER_REQUEST = 500;

	/** Matches any string with a non-space character (null passes, as with every constraint). */
	static final String NOT_BLANK = "(?s).*\\S.*";

	private PlaylistRequests() {
	}

	/**
	 * Body of {@code POST /playlists}.
	 *
	 * @param name the name
	 * @param description optional description
	 * @param visibility public (default), unlisted or private
	 * @param coverUrl optional cover image
	 * @param trackIds optional tracks to start with, in order
	 */
	record CreatePlaylistRequest(@NotBlank @Size(max = 120) String name, @Nullable @Size(max = 2000) String description,
			@Nullable @Size(max = 20) String visibility,
			@Nullable @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String coverUrl,
			@Nullable @Size(max = MAX_TRACKS_PER_REQUEST) List<@NotNull @Positive Integer> trackIds) {
	}

	/**
	 * Body of {@code PATCH /playlists/{id}} (received as a {@code PartialUpdate}): absent
	 * fields stay unchanged; {@code null} clears {@code description} and {@code cover_url}.
	 *
	 * @param name new name
	 * @param description new description
	 * @param visibility public, unlisted or private
	 * @param coverUrl new cover image
	 */
	record UpdatePlaylistRequest(@Nullable @Size(max = 120) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String name,
			@Nullable @Size(max = 2000) String description,
			@Nullable @Size(max = 20) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String visibility,
			@Nullable @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String coverUrl) {
	}

	/**
	 * Optional body of {@code POST /playlists/{id}/copy}.
	 *
	 * @param name name of the copy; defaults to the original's
	 */
	record CopyPlaylistRequest(@Nullable @Size(min = 1, max = 120) String name) {
	}

	/**
	 * Body of {@code POST /playlists/{id}/tracks}.
	 *
	 * @param trackIds the tracks to add, in order
	 * @param position where to insert them (1-based); appended at the end when absent
	 */
	record AddTracksRequest(@NotEmpty @Size(max = MAX_TRACKS_PER_REQUEST) List<@NotNull @Positive Integer> trackIds,
			@Nullable @Positive Integer position) {
	}

	/**
	 * Body of {@code POST /playlists/{id}/tracks/move}.
	 *
	 * @param from current position of the entry
	 * @param to its new position
	 */
	record MoveTrackRequest(@NotNull @Positive Integer from, @NotNull @Positive Integer to) {
	}

	/**
	 * Body of {@code PUT /playlists/{id}/collaborators/{username}}.
	 *
	 * @param role editor or viewer
	 */
	record CollaboratorRoleRequest(@NotBlank @Size(max = 20) String role) {
	}

}
