package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collaborators: editors change the track list, viewers can open a private playlist.
 */
@Service
class CollaboratorService {

	private final CollaboratorRepository collaborators;

	private final UserRepository users;

	private final PlaylistGuards guards;

	CollaboratorService(CollaboratorRepository collaborators, UserRepository users, PlaylistGuards guards) {
		this.collaborators = collaborators;
		this.users = users;
		this.guards = guards;
	}

	/** Visible to the owner and the collaborators themselves. */
	List<Collaborator> list(int playlistId, Caller caller) {
		PlaylistRepository.PlaylistAccess access = this.guards.viewable(playlistId, caller, false);
		if (!access.isOwner() && !access.isCollaborator()) {
			throw ApiException.forbidden("Only the owner and collaborators can see who collaborates on this playlist.");
		}
		return this.collaborators.list(playlistId);
	}

	/** Owner only. Returns the collaborator, and whether they were added (vs. role changed). */
	@Transactional
	Upserted put(int playlistId, String username, Caller caller, String role) {
		this.guards.owned(playlistId, caller, true);
		int userId = userId(username);
		if (userId == caller.userId()) {
			throw ApiException.unprocessable("owner_as_collaborator",
					"You own this playlist, so you can't also be a collaborator on it.", "username");
		}
		boolean added = this.collaborators.upsert(playlistId, userId, role);
		return new Upserted(this.collaborators.find(playlistId, userId), added);
	}

	/** The owner can remove anyone; a collaborator can remove themselves (leave). */
	@Transactional
	void remove(int playlistId, String username, Caller caller) {
		PlaylistRepository.PlaylistAccess access = this.guards.viewable(playlistId, caller, true);
		int userId = userId(username);
		if (!access.isOwner() && userId != caller.userId()) {
			throw ApiException.forbidden("Only the owner can remove other collaborators.");
		}
		this.collaborators.remove(playlistId, userId);
	}

	private int userId(String username) {
		return this.users.findUserId(username)
			.orElseThrow(() -> ApiException.notFound("No user named '" + username + "'."));
	}

	record Upserted(Collaborator collaborator, boolean added) {
	}

}
