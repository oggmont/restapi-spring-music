package com.oggmont.music.playlist;

import java.util.List;

/**
 * A page of a playlist's entries, in order, with the version they were read at.
 *
 * @param playlistId the playlist
 * @param version the playlist version (also sent as the ETag)
 * @param items the entries
 * @param limit page size
 * @param offset entries skipped
 * @param total number of entries in the playlist
 */
public record PlaylistTracksPage(int playlistId, int version, List<PlaylistEntry> items, int limit, int offset,
		long total) {
}
