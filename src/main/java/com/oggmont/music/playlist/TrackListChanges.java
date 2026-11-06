package com.oggmont.music.playlist;

import java.util.List;

/**
 * Responses of the track-list writes. Each carries the playlist's new version, also sent
 * as the {@code ETag} header.
 */
final class TrackListChanges {

	private TrackListChanges() {
	}

	/**
	 * @param playlistId the playlist
	 * @param added the new entries, in order
	 * @param version the new version
	 */
	record Added(int playlistId, List<AddedEntry> added, int version) {
	}

	/**
	 * @param position where it was added
	 * @param trackId the track
	 * @param addedBy username of who added it
	 */
	record AddedEntry(int position, int trackId, String addedBy) {
	}

	/**
	 * @param playlistId the playlist
	 * @param from old position
	 * @param to new position
	 * @param version the new version
	 */
	record Moved(int playlistId, int from, int to, int version) {
	}

	/**
	 * @param removed the entry that was removed
	 * @param version the new version
	 */
	record RemovedOne(RemovedEntry removed, int version) {
	}

	/**
	 * @param position where it was
	 * @param trackId the track
	 */
	record RemovedEntry(int position, int trackId) {
	}

	/**
	 * @param removed how many entries were removed
	 * @param version the new version
	 */
	record RemovedAll(int removed, int version) {
	}

}
