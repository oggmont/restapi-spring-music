package com.oggmont.music.admin;

import com.oggmont.music.auth.AuthService;
import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.auth.Role;
import com.oggmont.music.auth.UserAccountRepository;
import com.oggmont.music.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account administration: roles and passwords. Admins only.
 * <p>
 * Setting a password also lets an account without one (such as the seed users) log in.
 */
@RestController
@RequestMapping("/admin/users/{username}")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Account administration", description = "Admins: roles and passwords")
class AdminUserController {

	private final UserAccountRepository accounts;

	private final AuthService auth;

	AdminUserController(UserAccountRepository accounts, AuthService auth) {
		this.accounts = accounts;
		this.auth = auth;
	}

	@PutMapping("/role")
	@Operation(summary = "Set an account's role: listener, curator or admin")
	RoleResponse setRole(@PathVariable String username, @CurrentUser Caller caller,
			@Valid @RequestBody RoleRequest request) {
		int userId = userId(username);
		if (userId == caller.userId()) {
			throw ApiException.unprocessable("cannot_change_own_role", "You can't change your own role.", "role");
		}
		Role role = Role.fromCode(request.role());
		this.accounts.upsertRole(userId, role);
		return new RoleResponse(userId, username, role);
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Set an account's password (signs it out everywhere)")
	void setPassword(@PathVariable String username, @Valid @RequestBody PasswordRequest request) {
		this.auth.setPassword(userId(username), request.password());
	}

	private int userId(String username) {
		return this.accounts.findUserId(username)
			.orElseThrow(() -> ApiException.notFound("No user named '" + username + "'."));
	}

	/**
	 * @param role listener, curator or admin
	 */
	record RoleRequest(@NotNull @Pattern(regexp = "listener|curator|admin",
			message = "must be listener, curator or admin") String role) {
	}

	/**
	 * @param userId {@code app_user.user_id}
	 * @param username the account
	 * @param role its new role
	 */
	record RoleResponse(int userId, String username, Role role) {
	}

	/**
	 * @param password the new password, at least 8 characters
	 */
	record PasswordRequest(@NotBlank @Size(min = 8, max = 72) String password) {
	}

}
