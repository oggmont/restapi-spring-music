package com.oggmont.music.playlist;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An item of {@code GET /me/playlists}: the playlist's summary fields, plus what the
 * add-to-playlist menu needs.
 *
 * @param playlist the playlist (its fields appear at the top level)
 * @param myRole the caller's role: owner, editor or viewer
 * @param isFavorite whether the caller has it as a favorite
 * @param lastAddedAt when the caller last added a track to it; null if never ("last used")
 * @param containsTrack whether it holds the track given as {@code ?contains_track=}; only
 * sent with that parameter
 */
public record MyPlaylist(@JsonUnwrapped PlaylistSummary playlist, String myRole, boolean isFavorite,
		@Nullable Instant lastAddedAt, @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean containsTrack) {
}
