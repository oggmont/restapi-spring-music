package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.common.web.IfMatch;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A playlist's track list. Every write needs {@code If-Match} with the current version
 * (428 without it, 412 when stale) and the owner or editor role (403). The playlist row
 * stays locked from the version check to the end of the write.
 */
@Service
class PlaylistTrackService {

	private final PlaylistRepository playlists;

	private final PlaylistTrackRepository tracks;

	private final PlaylistGuards guards;

	PlaylistTrackService(PlaylistRepository playlists, PlaylistTrackRepository tracks, PlaylistGuards guards) {
		this.playlists = playlists;
		this.tracks = tracks;
		this.guards = guards;
	}

	@Transactional(readOnly = true)
	PlaylistTracksPage list(int playlistId, @Nullable Caller caller, PageParams page, @Nullable Integer afterPosition) {
		PlaylistRepository.PlaylistAccess access = this.guards.viewable(playlistId, caller, false);
		return page(playlistId, access.version(), caller, page, afterPosition);
	}

	@Transactional(readOnly = true)
	PlaylistTracksPage listShared(String shareToken, @Nullable Caller caller, PageParams page,
			@Nullable Integer afterPosition) {
		int playlistId = this.playlists.findIdByShareToken(PlaylistGuards.parseShareToken(shareToken))
			.orElseThrow(PlaylistGuards::shareLinkNotFound);
		return page(playlistId, this.tracks.version(playlistId), caller, page, afterPosition);
	}

	/**
	 * Appends, or inserts at {@code position} and pushes the following entries down. A
	 * playlist holds at most 100 entries ({@code 422 playlist_full}, saying how many fit).
	 */
	@Transactional
	TrackListChanges.Added add(int playlistId, Caller caller, PlaylistRequests.AddTracksRequest request, IfMatch ifMatch) {
		ifMatch.require();
		PlaylistRepository.PlaylistAccess access = this.guards.editable(playlistId, caller, true);
		ifMatch.check(access.version());
		long count = this.tracks.count(playlistId);
		if (count + request.trackIds().size() > PlaylistLimits.MAX_TRACKS) {
			throw PlaylistLimits.playlistFull(access.name(), count, request.trackIds().size());
		}
		int last = this.tracks.maxPosition(playlistId);
		int position = (request.position() != null) ? request.position() : last + 1;
		if (position > last + 1) {
			throw ApiException.unprocessable("invalid_position",
					"position must be between 1 and " + (last + 1) + " (the end of the playlist).", "position");
		}
		if (position <= last) {
			this.tracks.shift(playlistId, position, request.trackIds().size());
		}
		List<TrackListChanges.AddedEntry> added = this.tracks
			.insert(playlistId, request.trackIds(), position, caller.userId())
			.stream()
			.map((row) -> new TrackListChanges.AddedEntry(row.position(), row.trackId(), caller.username()))
			.toList();
		return new TrackListChanges.Added(playlistId, added, this.tracks.version(playlistId));
	}

	@Transactional
	TrackListChanges.Moved move(int playlistId, Caller caller, PlaylistRequests.MoveTrackRequest request, IfMatch ifMatch) {
		ifMatch.require();
		PlaylistRepository.PlaylistAccess access = this.guards.editable(playlistId, caller, true);
		ifMatch.check(access.version());
		int last = this.tracks.maxPosition(playlistId);
		int from = request.from();
		int to = request.to();
		if (from > last) {
			throw ApiException.unprocessable("invalid_position", "There is no entry at position " + from + ".", "from");
		}
		if (to > last) {
			throw ApiException.unprocessable("invalid_position", "to must be between 1 and " + last + ".", "to");
		}
		if (from != to) {
			this.tracks.move(playlistId, from, to);
		}
		return new TrackListChanges.Moved(playlistId, from, to, this.tracks.version(playlistId));
	}

	/** Removes one entry; the following entries move up one place. */
	@Transactional
	TrackListChanges.RemovedOne removeAt(int playlistId, Caller caller, int position, IfMatch ifMatch) {
		ifMatch.require();
		PlaylistRepository.PlaylistAccess access = this.guards.editable(playlistId, caller, true);
		ifMatch.check(access.version());
		TrackListChanges.RemovedEntry removed = this.tracks.removeAt(playlistId, position)
			.orElseThrow(() -> ApiException.notFound("Playlist " + playlistId + " has no entry at position " + position + "."));
		return new TrackListChanges.RemovedOne(removed, this.tracks.version(playlistId));
	}

	/** Removes every occurrence of a track, then renumbers the entries. */
	@Transactional
	TrackListChanges.RemovedAll removeTrack(int playlistId, Caller caller, int trackId, IfMatch ifMatch) {
		ifMatch.require();
		PlaylistRepository.PlaylistAccess access = this.guards.editable(playlistId, caller, true);
		ifMatch.check(access.version());
		int removed = this.tracks.removeTrack(playlistId, trackId);
		if (removed > 0) {
			this.tracks.renumber(playlistId);
		}
		return new TrackListChanges.RemovedAll(removed, this.tracks.version(playlistId));
	}

	private PlaylistTracksPage page(int playlistId, int version, @Nullable Caller caller, PageParams page,
			@Nullable Integer afterPosition) {
		List<PlaylistEntry> items = this.tracks.entries(playlistId, PlaylistGuards.idOf(caller), page.resolvedLimit(),
				page.resolvedOffset(), afterPosition);
		return new PlaylistTracksPage(playlistId, version, items, page.resolvedLimit(), page.resolvedOffset(),
				this.tracks.count(playlistId));
	}

}
