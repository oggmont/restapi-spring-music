package com.oggmont.music.playlist;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.paging.ItemList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collaborators of a playlist.
 */
@RestController
@RequestMapping("/playlists/{id}/collaborators")
@Tag(name = "Collaborators", description = "Share a playlist with editors and viewers")
class CollaboratorController {

	private final CollaboratorService collaborators;

	CollaboratorController(CollaboratorService collaborators) {
		this.collaborators = collaborators;
	}

	@GetMapping
	@Operation(summary = "List collaborators (owner and collaborators)")
	ItemList<Collaborator> list(@PathVariable int id, @CurrentUser Caller caller) {
		return ItemList.of(this.collaborators.list(id, caller));
	}

	@PutMapping("/{username}")
	@Operation(summary = "Add a collaborator (201) or change their role (200) (owner only)")
	ResponseEntity<Collaborator> put(@PathVariable int id, @PathVariable String username, @CurrentUser Caller caller,
			@Valid @RequestBody PlaylistRequests.CollaboratorRoleRequest request) {
		CollaboratorService.Upserted result = this.collaborators.put(id, username, caller, request.role());
		return ResponseEntity.status(result.added() ? HttpStatus.CREATED : HttpStatus.OK).body(result.collaborator());
	}

	@DeleteMapping("/{username}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Remove a collaborator, or leave the playlist yourself")
	void remove(@PathVariable int id, @PathVariable String username, @CurrentUser Caller caller) {
		this.collaborators.remove(id, username, caller);
	}

}
