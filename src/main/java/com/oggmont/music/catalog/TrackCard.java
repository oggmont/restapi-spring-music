package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A track as it appears in lists (playlists, libraries, feeds).
 *
 * @param trackId {@code track.track_id}
 * @param name the track's name
 * @param durationMs running time ({@code track.milliseconds})
 * @param duration running time for display, e.g. {@code "4:20"}
 * @param artists who performs it ({@code track_detail.track_artists})
 * @param album the album it is on
 * @param isLiked whether the caller liked it; only sent with a token
 */
public record TrackCard(int trackId, String name, long durationMs, String duration, List<String> artists, AlbumRef album,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean isLiked) {
}
