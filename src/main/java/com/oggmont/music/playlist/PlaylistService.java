package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.jdbc.ColumnChanges;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.common.web.IfMatch;
import com.oggmont.music.common.web.PartialUpdate;
import com.oggmont.music.user.UserRepository;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Playlists and their details.
 */
@Service
class PlaylistService {

	private final PlaylistRepository playlists;

	private final PlaylistTrackRepository tracks;

	private final UserRepository users;

	private final PlaylistGuards guards;

	PlaylistService(PlaylistRepository playlists, PlaylistTrackRepository tracks, UserRepository users,
			PlaylistGuards guards) {
		this.playlists = playlists;
		this.tracks = tracks;
		this.users = users;
		this.guards = guards;
	}

	PlaylistDetail get(int playlistId, @Nullable Caller caller) {
		return this.playlists.findDetail(playlistId, PlaylistGuards.idOf(caller))
			.orElseThrow(() -> PlaylistGuards.notFound(playlistId));
	}

	PlaylistDetail getShared(String shareToken, @Nullable Caller caller) {
		UUID token = PlaylistGuards.parseShareToken(shareToken);
		return this.playlists.findDetailByShareToken(token, PlaylistGuards.idOf(caller))
			.orElseThrow(PlaylistGuards::shareLinkNotFound);
	}

	/** Creates the playlist and its first entries in one transaction. */
	@Transactional
	PlaylistDetail create(Caller caller, PlaylistRequests.CreatePlaylistRequest request) {
		int playlistId = this.playlists.insert(caller.userId(), request.name().trim(), request.description(),
				request.visibility(), request.coverUrl());
		if (request.trackIds() != null && !request.trackIds().isEmpty()) {
			this.tracks.insert(playlistId, request.trackIds(), 1, caller.userId());
		}
		return get(playlistId, caller);
	}

	/** Owner only. {@code If-Match} is optional here; when sent it must match. */
	@Transactional
	PlaylistDetail update(int playlistId, Caller caller, PartialUpdate<PlaylistRequests.UpdatePlaylistRequest> patch,
			IfMatch ifMatch) {
		patch.rejectNulls("name", "visibility");
		PlaylistRepository.PlaylistAccess access = this.guards.owned(playlistId, caller, true);
		ifMatch.check(access.version());
		PlaylistRequests.UpdatePlaylistRequest request = patch.value();
		ColumnChanges changes = new ColumnChanges();
		if (patch.has("name")) {
			changes.set("name", Objects.requireNonNull(request.name()).trim(), Types.VARCHAR);
		}
		if (patch.has("description")) {
			changes.set("description", request.description(), Types.VARCHAR);
		}
		if (patch.has("visibility")) {
			changes.set("visibility", Objects.requireNonNull(request.visibility()).trim(), Types.VARCHAR);
		}
		if (patch.has("cover_url")) {
			changes.set("cover_url", request.coverUrl(), Types.VARCHAR);
		}
		this.playlists.update(playlistId, changes);
		return get(playlistId, caller);
	}

	/** Owner only. Entries, collaborators and follows are deleted with it (cascade). */
	@Transactional
	void delete(int playlistId, Caller caller, IfMatch ifMatch) {
		PlaylistRepository.PlaylistAccess access = this.guards.owned(playlistId, caller, true);
		ifMatch.check(access.version());
		this.playlists.delete(playlistId);
	}

	/** Owner only: a new share link; the old one stops working at once. */
	@Transactional
	UUID newShareToken(int playlistId, Caller caller) {
		this.guards.owned(playlistId, caller, true);
		return this.playlists.regenerateShareToken(playlistId);
	}

	/** Copies a playlist the caller can view into a new private playlist the caller owns. */
	@Transactional
	PlaylistDetail copy(int playlistId, Caller caller, @Nullable String name) {
		this.guards.viewable(playlistId, caller, false);
		int copyId = this.playlists.copy(playlistId, caller.userId(), (name != null) ? name.trim() : null);
		this.tracks.copy(playlistId, copyId, caller.userId());
		return get(copyId, caller);
	}

	Page<PlaylistSummary> mine(Caller caller, PageParams page) {
		return this.playlists.findForMember(caller.userId(), page);
	}

	Page<PlaylistSummary> publicPlaylistsOf(String username, PageParams page) {
		int ownerId = this.users.findUserId(username)
			.orElseThrow(() -> ApiException.notFound("No user named '" + username + "'."));
		return this.playlists.findPublicByOwner(ownerId, page);
	}

	Page<FeedItem> feed(Caller caller, PageParams page) {
		return this.playlists.feed(caller.userId(), page);
	}

}
