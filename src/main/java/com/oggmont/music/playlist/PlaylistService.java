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
import java.util.List;
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

	/**
	 * Creates the playlist and its first entries in one transaction. It starts private;
	 * favoriting it makes it public. At most 20 owned playlists and 100 tracks (the
	 * database enforces both; the checks here only give clearer messages).
	 */
	@Transactional
	PlaylistDetail create(Caller caller, PlaylistRequests.CreatePlaylistRequest request) {
		if (request.visibility() != null) {
			throw PlaylistLimits.visibilityFollowsFavorites();
		}
		List<Integer> trackIds = (request.trackIds() != null) ? request.trackIds() : List.of();
		if (trackIds.size() > PlaylistLimits.MAX_TRACKS) {
			throw PlaylistLimits.tooManyForNewPlaylist(trackIds.size());
		}
		requireRoomForAnotherPlaylist(caller);
		int playlistId = this.playlists.insert(caller.userId(), request.name().trim(), request.description(),
				request.coverUrl());
		if (!trackIds.isEmpty()) {
			this.tracks.insert(playlistId, trackIds, 1, caller.userId());
		}
		return get(playlistId, caller);
	}

	/** Owner only. {@code If-Match} is optional here; when sent it must match. */
	@Transactional
	PlaylistDetail update(int playlistId, Caller caller, PartialUpdate<PlaylistRequests.UpdatePlaylistRequest> patch,
			IfMatch ifMatch) {
		if (patch.has("visibility")) {
			throw PlaylistLimits.visibilityFollowsFavorites();
		}
		patch.rejectNulls("name");
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

	/**
	 * Copies a playlist the caller can view into a new private playlist the caller owns.
	 * The copy counts toward the caller's 20 playlists and holds at most 100 tracks.
	 */
	@Transactional
	PlaylistDetail copy(int playlistId, Caller caller, @Nullable String name) {
		this.guards.viewable(playlistId, caller, false);
		long count = this.tracks.count(playlistId);
		if (count > PlaylistLimits.MAX_TRACKS) {
			throw PlaylistLimits.tooManyForNewPlaylist((int) count);
		}
		requireRoomForAnotherPlaylist(caller);
		int copyId = this.playlists.copy(playlistId, caller.userId(), (name != null) ? name.trim() : null);
		this.tracks.copy(playlistId, copyId, caller.userId());
		return get(copyId, caller);
	}

	Page<MyPlaylist> mine(Caller caller, @Nullable String role, PlaylistRepository.MemberSort sort,
			@Nullable Integer containsTrack, PageParams page) {
		return this.playlists.findForMember(caller.userId(), role, sort, containsTrack, page);
	}

	/** Random public playlists by other users that the caller hasn't favorited. */
	List<PlaylistSummary> random(@Nullable Caller caller, int limit, List<Integer> exclude) {
		return this.playlists.random(PlaylistGuards.idOf(caller), limit,
				exclude.stream().mapToInt(Integer::intValue).toArray());
	}

	private void requireRoomForAnotherPlaylist(Caller caller) {
		if (this.playlists.countOwned(caller.userId()) >= PlaylistLimits.MAX_OWNED) {
			throw PlaylistLimits.ownedLimitReached();
		}
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
