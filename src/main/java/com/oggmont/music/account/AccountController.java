package com.oggmont.music.account;

import com.oggmont.music.auth.AuthService;
import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.ApiPaths;
import com.oggmont.music.common.web.PartialUpdate;
import com.oggmont.music.user.UserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and the caller's account ({@code /me}).
 */
@RestController
@Tag(name = "Account", description = "Sign up and manage your own account")
class AccountController {

	private final AccountService accounts;

	private final AuthService auth;

	AccountController(AccountService accounts, AuthService auth) {
		this.accounts = accounts;
		this.auth = auth;
	}

	@PostMapping("/users")
	@Operation(summary = "Sign up: create an account (then log in with POST /auth/login)", security = {})
	ResponseEntity<UserProfile> signUp(@Valid @RequestBody SignUpRequest request) {
		UserProfile profile = this.accounts.signUp(request);
		return ResponseEntity.created(URI.create(ApiPaths.BASE + "/users/" + profile.username())).body(profile);
	}

	@GetMapping("/me")
	@Operation(summary = "Your profile, including email and role")
	UserProfile me(@CurrentUser Caller caller) {
		return this.accounts.me(caller);
	}

	@PatchMapping("/me")
	@Operation(summary = "Edit display name, bio, avatar or country (send only what changes)")
	UserProfile update(@CurrentUser Caller caller, @Valid @RequestBody PartialUpdate<UpdateProfileRequest> request) {
		return this.accounts.update(caller, request);
	}

	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete your account and everything it owns")
	void delete(@CurrentUser Caller caller) {
		this.accounts.delete(caller);
	}

	@PutMapping("/me/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Change your password (signs out your other sessions)")
	void changePassword(@CurrentUser Caller caller, @Valid @RequestBody ChangePasswordRequest request) {
		this.auth.changePassword(caller, request.currentPassword(), request.newPassword());
	}

	/**
	 * @param currentPassword the password in use
	 * @param newPassword the new password, at least 8 characters
	 */
	record ChangePasswordRequest(@NotBlank @Size(max = 1024) String currentPassword,
			@NotBlank @Size(min = 8, max = 72) String newPassword) {
	}

}
