package com.oggmont.music.user;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public user profiles. ({@code /users/{username}/playlists} is served by the playlist
 * module.)
 */
@RestController
@RequestMapping("/users/{username}")
@Tag(name = "Users", description = "Public profiles and who follows whom")
class UserController {

	private final UserRepository users;

	UserController(UserRepository users) {
		this.users = users;
	}

	@GetMapping
	@Operation(summary = "Public profile")
	UserProfile profile(@PathVariable String username, @CurrentUser @Nullable Caller caller) {
		return this.users.findPublicProfile(username, (caller != null) ? caller.userId() : null)
			.orElseThrow(() -> noSuchUser(username));
	}

	@GetMapping("/followers")
	@Operation(summary = "Who follows them, newest first")
	Page<UserCard> followers(@PathVariable String username, @Valid PageParams page) {
		return this.users.followers(userId(username), page);
	}

	@GetMapping("/following")
	@Operation(summary = "Users they follow, newest first")
	Page<UserCard> following(@PathVariable String username, @Valid PageParams page) {
		return this.users.following(userId(username), page);
	}

	private int userId(String username) {
		return this.users.findUserId(username).orElseThrow(() -> noSuchUser(username));
	}

	static ApiException noSuchUser(String username) {
		return ApiException.notFound("No user named '" + username + "'.");
	}

}
