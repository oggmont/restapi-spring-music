package com.oggmont.music.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
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

	static final int MAX_TRACKS_PER_REQUEST = PlaylistLimits.MAX_TRACKS;

	/** Matches any string with a non-space character (null passes, as with every constraint). */
	static final String NOT_BLANK = "(?s).*\\S.*";

	private PlaylistRequests() {
	}

	/**
	 * Body of {@code POST /playlists}.
	 *
	 * New playlists are always private: visibility follows favorites, so sending
	 * {@code visibility} answers {@code 422 visibility_follows_favorites}. More than 100
	 * tracks answer {@code 422 playlist_full}.
	 *
	 * @param name the name
	 * @param description optional description
	 * @param visibility not accepted; only read to refuse it
	 * @param coverUrl optional cover image
	 * @param trackIds optional tracks to start with, in order (at most 100)
	 */
	record CreatePlaylistRequest(@NotBlank @Size(max = 120) String name, @Nullable @Size(max = 2000) String description,
			@Schema(hidden = true) @Nullable Object visibility,
			@Nullable @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String coverUrl,
			@Nullable List<@NotNull @Positive Integer> trackIds) {
	}

	/**
	 * Body of {@code PATCH /playlists/{id}} (received as a {@code PartialUpdate}): absent
	 * fields stay unchanged; {@code null} clears {@code description} and {@code cover_url}.
	 * {@code visibility} can't be changed: it follows favorites
	 * ({@code 422 visibility_follows_favorites}).
	 *
	 * @param name new name
	 * @param description new description
	 * @param coverUrl new cover image
	 */
	record UpdatePlaylistRequest(@Nullable @Size(max = 120) @Pattern(regexp = NOT_BLANK, message = "must not be blank") String name,
			@Nullable @Size(max = 2000) String description,
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
