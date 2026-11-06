package com.oggmont.music.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, token refresh and logout. The API is its own token issuer: access tokens are
 * short-lived JWTs, refresh tokens are opaque and rotate on every use.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Log in and manage tokens")
class AuthController {

	private final AuthService auth;

	AuthController(AuthService auth) {
		this.auth = auth;
	}

	@PostMapping("/login")
	@Operation(summary = "Log in with a username (or email) and password", security = {})
	ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return noStore(this.auth.login(request.username(), request.password()));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Exchange a refresh token for new tokens", security = {})
	ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return noStore(this.auth.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "End the session a refresh token belongs to", security = {})
	void logout(@Valid @RequestBody RefreshRequest request) {
		this.auth.logout(request.refreshToken());
	}

	private static ResponseEntity<TokenResponse> noStore(TokenResponse tokens) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(tokens);
	}

	/**
	 * @param username username or email
	 * @param password the password
	 */
	record LoginRequest(@NotBlank @Size(max = 320) String username, @NotBlank @Size(max = 1024) String password) {
	}

	/**
	 * @param refreshToken a refresh token from login or a previous refresh
	 */
	record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
	}

}
