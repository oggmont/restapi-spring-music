package com.oggmont.music;

import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class of the integration tests: the whole application (security filters
 * included) against a Testcontainers copy of the music database, driven through MockMvc.
 * <p>
 * The context, and so the database, is shared by every test class. Tests create their
 * own users and playlists and don't depend on what other tests changed.
 */
@SpringBootTest(properties = { "app.admin.username=" + IntegrationTest.ADMIN_USERNAME,
		"app.admin.password=" + IntegrationTest.ADMIN_PASSWORD,
		"app.jwt.secret=dGVzdC1zaWduaW5nLWtleS1mb3ItaW50ZWdyYXRpb24tdGVzdHMtb25seQ==",
		"app.admin.email=admin.test@example.com" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

	protected static final String API = "/api/v1";

	protected static final String ADMIN_USERNAME = "admin.test";

	protected static final String ADMIN_PASSWORD = "admin-test-password";

	protected static final String SEED_PASSWORD = "seed-user-password";

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected JdbcClient jdbc;

	protected String adminToken() {
		return login(ADMIN_USERNAME, ADMIN_PASSWORD);
	}

	protected String login(String username, String password) {
		MvcTestResult result = this.mvc.post()
			.uri(API + "/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "%s", "password": "%s"}""".formatted(username, password))
			.exchange();
		assertThat(result).hasStatusOk();
		return read(result, "$.access_token");
	}

	/** Signs up a new listener with a unique username, and logs in. */
	protected TestUser newUser(String prefix) {
		String username = prefix + "." + Long.toString(ThreadLocalRandom.current().nextLong(1L << 40), 36);
		MvcTestResult result = this.mvc.post()
			.uri(API + "/users")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "%s", "display_name": "Test %s", "email": "%s@example.com", "password": "%s"}"""
				.formatted(username, prefix, username, SEED_PASSWORD))
			.exchange();
		assertThat(result).hasStatus(201);
		int userId = read(result, "$.user_id");
		return new TestUser(userId, username, login(username, SEED_PASSWORD));
	}

	/** Gives a seed user (ana.garcia, ben.carter...) a password, as an admin would, and logs in. */
	protected String seedUser(String username) {
		assertThat(this.mvc.put()
			.uri(API + "/admin/users/{username}/password", username)
			.header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"password": "%s"}""".formatted(SEED_PASSWORD))).hasStatus(204);
		return login(username, SEED_PASSWORD);
	}

	/** Creates a playlist owned by the token's user; returns its ID. */
	protected int createPlaylist(String token, String name, String visibility, int... trackIds) {
		StringBuilder ids = new StringBuilder();
		for (int id : trackIds) {
			ids.append(ids.isEmpty() ? "" : ",").append(id);
		}
		MvcTestResult result = this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "%s", "visibility": "%s", "track_ids": [%s]}""".formatted(name, visibility, ids))
			.exchange();
		assertThat(result).hasStatus(201);
		return read(result, "$.playlist_id");
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

	protected static <T> T read(MvcTestResult result, String jsonPath) {
		return JsonPath.read(body(result), jsonPath);
	}

	protected static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @param userId {@code app_user.user_id}
	 * @param username the generated username
	 * @param token an access token
	 */
	protected record TestUser(int userId, String username, String token) {

		public String bearer() {
			return IntegrationTest.bearer(this.token);
		}

	}

}
