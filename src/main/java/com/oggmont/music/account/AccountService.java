package com.oggmont.music.account;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.PasswordPolicy;
import com.oggmont.music.auth.Role;
import com.oggmont.music.auth.UserAccountRepository;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.jdbc.ColumnChanges;
import com.oggmont.music.common.web.PartialUpdate;
import com.oggmont.music.user.UserProfile;
import com.oggmont.music.user.UserRepository;
import java.sql.Types;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-up and the caller's own account.
 */
@Service
class AccountService {

	private final AccountRepository account;

	private final UserRepository users;

	private final UserAccountRepository credentials;

	private final PasswordEncoder passwordEncoder;

	private final TransactionTemplate transactions;

	AccountService(AccountRepository account, UserRepository users, UserAccountRepository credentials,
			PasswordEncoder passwordEncoder, TransactionTemplate transactions) {
		this.account = account;
		this.users = users;
		this.credentials = credentials;
		this.passwordEncoder = passwordEncoder;
		this.transactions = transactions;
	}

	/** Creates the profile and its credentials in one transaction. */
	UserProfile signUp(SignUpRequest request) {
		PasswordPolicy.check(request.password(), "password");
		String passwordHash = this.passwordEncoder.encode(request.password()); // outside the transaction: slow on purpose
		this.transactions.executeWithoutResult((status) -> {
			int userId = this.account.insert(request.username(), request.displayName().trim(), request.email().trim(),
					upper(request.countryCode()));
			this.credentials.insertAccount(userId, Role.LISTENER, passwordHash);
		});
		// The response is the public profile, as in API-Design.md (no follow flag: anonymous view).
		return this.users.findPublicProfile(request.username(), null).orElseThrow();
	}

	UserProfile me(Caller caller) {
		return this.users.findOwnProfile(caller.userId())
			.orElseThrow(() -> ApiException.notFound("Your account no longer exists."));
	}

	UserProfile update(Caller caller, PartialUpdate<UpdateProfileRequest> patch) {
		if (patch.has("username")) {
			throw ApiException.unprocessable("username_immutable",
					"Usernames can't be changed, because they appear in URLs.", "username");
		}
		patch.rejectNulls("display_name");
		UpdateProfileRequest request = patch.value();
		ColumnChanges changes = new ColumnChanges();
		if (patch.has("display_name")) {
			changes.set("display_name", Objects.requireNonNull(request.displayName()).trim(), Types.VARCHAR);
		}
		if (patch.has("bio")) {
			changes.set("bio", request.bio(), Types.VARCHAR);
		}
		if (patch.has("avatar_url")) {
			changes.set("avatar_url", request.avatarUrl(), Types.VARCHAR);
		}
		if (patch.has("country_code")) {
			changes.set("country_code", upper(request.countryCode()), Types.CHAR);
		}
		this.account.update(caller.userId(), changes);
		return me(caller);
	}

	void delete(Caller caller) {
		this.account.delete(caller.userId());
	}

	private static @Nullable String upper(@Nullable String countryCode) {
		return (countryCode != null) ? countryCode.trim().toUpperCase(Locale.ROOT) : null;
	}

}
