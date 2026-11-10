package com.oggmont.music.follow;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.oggmont.music.playlist.PlaylistSummary;
import java.time.Instant;

/**
 * One of a user's favorites ({@code GET /users/{username}/favorites}): the playlist's
 * summary fields, plus when it became a favorite.
 *
 * @param playlist the playlist (its fields appear at the top level)
 * @param favoritedAt when the user made it a favorite
 */
record FavoritePlaylist(@JsonUnwrapped PlaylistSummary playlist, Instant favoritedAt) {
}
