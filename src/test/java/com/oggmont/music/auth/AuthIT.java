package com.oggmont.music.auth;

import com.oggmont.music.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login, tokens, the super user and account lifecycle.
 */
class AuthIT extends IntegrationTest {

	@Test
	void superUserIsCreatedAtStartupAsAnAdmin() {
		String token = adminToken();
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.hasStatusOk()
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.username").isEqualTo("admin.test");
				assertThat(json).extractingPath("$.role").isEqualTo("admin");
				assertThat(json).extractingPath("$.is_verified").isEqualTo(true);
				assertThat(json).extractingPath("$.email").isEqualTo("admin.test@example.com");
			});
	}

	@Test
	void loginReturnsAnOAuthStyleTokenResponse() {
		MvcTestResult result = login(ADMIN_USERNAME, ADMIN_PASSWORD, 200);
		assertThat(result).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store")
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.token_type").isEqualTo("Bearer");
				assertThat(json).extractingPath("$.expires_in").isEqualTo(900);
				assertThat(json).extractingPath("$.refresh_token").asString().hasSizeGreaterThan(30);
			});
	}

	@Test
	void wrongPasswordAndUnknownUserGetTheSameAnswer() {
		assertThat(login(ADMIN_USERNAME, "wrong", 401)).bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_credentials");
		assertThat(login("nobody.here", "whatever1", 401)).bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_credentials");
	}

	@Test
	void seedUsersCantLogInUntilTheyHaveAPassword() {
		// The seed users have no credentials of their own.
		assertThat(login("chloe.martin", "anything1", 401)).bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_credentials");
	}

	@Test
	void requestsWithoutOrWithABadTokenAreRejected() {
		assertThat(this.mvc.get().uri(API + "/me")).hasStatus(401)
			.containsHeader(HttpHeaders.WWW_AUTHENTICATE)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("unauthorized");
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")).hasStatus(401)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_token");
		String token = adminToken();
		String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered))).hasStatus(401);
	}

	@Test
	void signUpCreatesAProfileThatCanLogIn() {
		TestUser user = newUser("signup");
		assertThat(this.mvc.get().uri(API + "/users/{username}", user.username())).hasStatusOk()
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.display_name").isEqualTo("Test signup");
				assertThat(json).doesNotHavePath("$.email");
				assertThat(json).doesNotHavePath("$.is_followed_by_me");
			});
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, user.bearer())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.role")
			.isEqualTo("listener");
		// Login by email works too.
		assertThat(login(user.username() + "@EXAMPLE.com", SEED_PASSWORD, 200)).hasStatusOk();
	}

	@Test
	void signUpAnswersWithLocationAndRejectsTakenNamesAndBadInput() {
		TestUser user = newUser("taken");
		assertThat(signUp(user.username(), "other@example.com", "long-enough-1")).hasStatus(409)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("username_taken");
		assertThat(signUp("fresh.name.x1", user.username().toUpperCase() + "@Example.com", "long-enough-1"))
			.hasStatus(409)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("email_taken");
		assertThat(signUp("Bad Name", "bad@example.com", "long-enough-1")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.details[0].field")
			.isEqualTo("username");
		assertThat(signUp("short.pw", "short.pw@example.com", "short")).hasStatus(422);
		MvcTestResult created = signUp("located." + System.nanoTime() % 100000, null, "long-enough-1");
		assertThat(created).hasStatus(201).containsHeader(HttpHeaders.LOCATION);
		assertThat(created.getResponse().getHeader(HttpHeaders.LOCATION)).startsWith("/api/v1/users/located.");
	}

	@Test
	void refreshTokensRotateAndReuseRevokesTheSession() {
		TestUser user = newUser("refresh");
		MvcTestResult login = login(user.username(), SEED_PASSWORD, 200);
		String first = read(login, "$.refresh_token");

		MvcTestResult refreshed = refresh(first);
		assertThat(refreshed).hasStatusOk();
		String second = read(refreshed, "$.refresh_token");
		assertThat(second).isNotEqualTo(first);
		String access = read(refreshed, "$.access_token");
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, bearer(access))).hasStatusOk();

		// Using the first token again looks like theft: the whole session is revoked.
		assertThat(refresh(first)).hasStatus(401).bodyJson().extractingPath("$.error.code").isEqualTo("invalid_grant");
		assertThat(refresh(second)).hasStatus(401);
	}

	@Test
	void logoutEndsTheSession() {
		TestUser user = newUser("logout");
		String refreshToken = read(login(user.username(), SEED_PASSWORD, 200), "$.refresh_token");
		assertThat(this.mvc.post()
			.uri(API + "/auth/logout")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"refresh_token": "%s"}""".formatted(refreshToken))).hasStatus(204);
		assertThat(refresh(refreshToken)).hasStatus(401);
	}

	@Test
	void changingThePasswordRequiresTheCurrentOneAndEndsOtherSessions() {
		TestUser user = newUser("passwd");
		String refreshToken = read(login(user.username(), SEED_PASSWORD, 200), "$.refresh_token");
		assertThat(changePassword(user, "not-the-password", "a-brand-new-password")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("wrong_password");
		assertThat(changePassword(user, SEED_PASSWORD, "a-brand-new-password")).hasStatus(204);
		assertThat(login(user.username(), SEED_PASSWORD, 401)).hasStatus(401);
		assertThat(login(user.username(), "a-brand-new-password", 200)).hasStatusOk();
		assertThat(refresh(refreshToken)).hasStatus(401);
	}

	@Test
	void deletingTheAccountInvalidatesItsTokens() {
		TestUser user = newUser("delete");
		assertThat(this.mvc.delete().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, user.bearer())).hasStatus(204);
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, user.bearer())).hasStatus(401);
		assertThat(login(user.username(), SEED_PASSWORD, 401)).hasStatus(401);
		assertThat(this.mvc.get().uri(API + "/users/{username}", user.username())).hasStatus(404);
	}

	private MvcTestResult login(String username, String password, int expectedStatus) {
		MvcTestResult result = this.mvc.post()
			.uri(API + "/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "%s", "password": "%s"}""".formatted(username, password))
			.exchange();
		assertThat(result).hasStatus(expectedStatus);
		return result;
	}

	private MvcTestResult refresh(String refreshToken) {
		return this.mvc.post()
			.uri(API + "/auth/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"refresh_token": "%s"}""".formatted(refreshToken))
			.exchange();
	}

	private MvcTestResult signUp(String username, String email, String password) {
		String mail = (email != null) ? email : username + "@example.com";
		return this.mvc.post()
			.uri(API + "/users")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "%s", "display_name": "Someone", "email": "%s", "password": "%s", "country_code": "es"}"""
				.formatted(username, mail, password))
			.exchange();
	}

	private MvcTestResult changePassword(TestUser user, String current, String next) {
		return this.mvc.put()
			.uri(API + "/me/password")
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"current_password": "%s", "new_password": "%s"}""".formatted(current, next))
			.exchange();
	}

}
