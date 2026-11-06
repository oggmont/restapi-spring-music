package com.oggmont.music.auth;

import com.oggmont.music.common.error.ApiException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.function.SingletonSupplier;

/**
 * Logging in, refreshing and revoking tokens, and changing passwords.
 */
@Service
public class AuthService {

	private final UserAccountRepository accounts;

	private final RefreshTokenService refreshTokens;

	private final TokenService tokens;

	private final PasswordEncoder passwordEncoder;

	private final JwtProperties properties;

	/** Compared against when an account doesn't exist, so the response time doesn't reveal it. */
	private final Supplier<String> dummyHash;

	AuthService(UserAccountRepository accounts, RefreshTokenService refreshTokens, TokenService tokens,
			PasswordEncoder passwordEncoder, JwtProperties properties) {
		this.accounts = accounts;
		this.refreshTokens = refreshTokens;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.dummyHash = SingletonSupplier.of(() -> passwordEncoder.encode(UUID.randomUUID().toString()));
	}

	/** Checks a username (or email) and password and starts a new session. */
	public TokenResponse login(String login, String password) {
		Optional<UserAccountRepository.LoginAccount> found = this.accounts.findLogin(login.trim());
		String hash = found.map(UserAccountRepository.LoginAccount::passwordHash).orElse(null);
		if (hash == null) {
			this.passwordEncoder.matches(password, this.dummyHash.get());
			throw invalidCredentials();
		}
		if (!this.passwordEncoder.matches(password, hash)) {
			throw invalidCredentials();
		}
		UserAccountRepository.LoginAccount account = found.get();
		if (this.passwordEncoder.upgradeEncoding(hash)) {
			this.accounts.updatePasswordHash(account.userId(), this.passwordEncoder.encode(password));
		}
		return tokenResponse(account, this.refreshTokens.issue(account.userId()));
	}

	/** Exchanges a refresh token for new tokens (the refresh token is rotated). */
	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotated rotated = this.refreshTokens.rotate(refreshToken)
			.orElseThrow(() -> ApiException.unauthorized("invalid_grant",
					"The refresh token is invalid, expired or already used. Sign in again."));
		UserAccountRepository.LoginAccount account = this.accounts.findLogin(rotated.userId())
			.orElseThrow(() -> ApiException.unauthorized("invalid_grant", "The account no longer exists."));
		return tokenResponse(account, rotated.token());
	}

	/** Ends the session the refresh token belongs to. */
	public void logout(String refreshToken) {
		this.refreshTokens.revokeFamily(refreshToken);
	}

	/** Changes the caller's password and ends all their sessions. */
	@Transactional
	public void changePassword(Caller caller, String currentPassword, String newPassword) {
		String hash = this.accounts.findLogin(caller.userId())
			.map(UserAccountRepository.LoginAccount::passwordHash)
			.orElse(null);
		if (hash == null || !this.passwordEncoder.matches(currentPassword, hash)) {
			throw ApiException.unprocessable("wrong_password", "The current password is wrong.", "current_password");
		}
		PasswordPolicy.check(newPassword, "new_password");
		this.accounts.upsertPassword(caller.userId(), this.passwordEncoder.encode(newPassword));
		this.refreshTokens.revokeAll(caller.userId());
	}

	/** Sets the password of any account (admins) and ends its sessions. */
	@Transactional
	public void setPassword(int userId, String newPassword) {
		PasswordPolicy.check(newPassword, "password");
		this.accounts.upsertPassword(userId, this.passwordEncoder.encode(newPassword));
		this.accounts.ensureSubject(userId);
		this.refreshTokens.revokeAll(userId);
	}

	/** Creates the credentials of a new account (sign-up). */
	public void createCredentials(int userId, String password) {
		this.accounts.insertAccount(userId, Role.LISTENER, this.passwordEncoder.encode(password));
	}

	private TokenResponse tokenResponse(UserAccountRepository.LoginAccount account,
			RefreshTokenService.IssuedToken refreshToken) {
		String subject = (account.authSubject() != null) ? account.authSubject()
				: this.accounts.ensureSubject(account.userId());
		TokenService.AccessToken accessToken = this.tokens.issueAccessToken(subject, account.username(), account.role());
		// expires_in is the token's lifetime (RFC 6749, section 5.1), not a countdown.
		return new TokenResponse(accessToken.value(), "Bearer", this.properties.accessTokenTtl().toSeconds(),
				refreshToken.value(), this.properties.refreshTokenTtl().toSeconds());
	}

	private static ApiException invalidCredentials() {
		return ApiException.unauthorized("invalid_credentials", "Wrong username or password.");
	}

}
