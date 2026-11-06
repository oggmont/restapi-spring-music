package com.oggmont.music.auth;

import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Creates the super user from {@code APP_ADMIN_USERNAME} / {@code APP_ADMIN_PASSWORD}
 * when the API starts, or makes an existing account with that username an admin.
 * <p>
 * The password is only applied to an account that has none, so a password changed
 * later through the API is kept across restarts.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.]{3,30}$");

	private static final int RECOMMENDED_PASSWORD_LENGTH = 12;

	private final AdminProperties properties;

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final TransactionTemplate transactions;

	AdminBootstrap(AdminProperties properties, UserAccountRepository accounts, PasswordEncoder passwordEncoder,
			TransactionTemplate transactions) {
		this.properties = properties;
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.transactions = transactions;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.properties.isConfigured()) {
			log.info("No super user configured (set APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD to create one)");
			return;
		}
		String username = Objects.requireNonNull(this.properties.username()).trim();
		String password = Objects.requireNonNull(this.properties.password());
		if (!USERNAME.matcher(username).matches()) {
			throw new IllegalStateException(
					"APP_ADMIN_USERNAME must be 3 to 30 lowercase letters, digits, '_' or '.', but is '" + username + "'");
		}
		String hash = this.passwordEncoder.encode(password);
		this.transactions.executeWithoutResult((status) -> ensureAdmin(username, hash));
		if (password.length() < RECOMMENDED_PASSWORD_LENGTH) {
			log.warn("The super user's password is short. Change it (PUT /api/v1/me/password) before exposing the API "
					+ "beyond your machine.");
		}
	}

	private void ensureAdmin(String username, String passwordHash) {
		Integer existing = this.accounts.findUserId(username).orElse(null);
		if (existing == null) {
			String email = StringUtils.hasText(this.properties.email()) ? this.properties.email().trim()
					: username + "@localhost";
			String displayName = StringUtils.hasText(this.properties.displayName())
					? this.properties.displayName().trim() : username;
			int userId = this.accounts.insertUser(username, displayName, email, null, true)
				.orElseThrow(() -> new IllegalStateException(
						"Couldn't create the super user '" + username + "': the email " + email + " is already taken"));
			this.accounts.insertAccount(userId, Role.ADMIN, passwordHash);
			log.info("Created super user '{}' (user {})", username, userId);
			return;
		}
		boolean hadPassword = this.accounts.findLogin(existing)
			.map((account) -> account.passwordHash() != null)
			.orElse(false);
		this.accounts.grantAdmin(existing, passwordHash);
		this.accounts.ensureSubject(existing);
		if (hadPassword) {
			log.info("Super user '{}' is ready (user {}); its existing password was kept", username, existing);
		}
		else {
			log.info("Super user '{}' is ready (user {}); its password was set from APP_ADMIN_PASSWORD", username,
					existing);
		}
	}

}
