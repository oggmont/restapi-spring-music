package com.oggmont.music.common;

import com.oggmont.music.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every error uses the envelope from API-Design.md: {@code {"error": {"code", "message"}}}.
 */
class ErrorHandlingIT extends IntegrationTest {

	@Test
	void unknownEndpointsAndUnsupportedVersions() {
		String token = newUser("errors").token();
		assertThat(this.mvc.get().uri(API + "/no-such-thing").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.hasStatus(404)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("not_found");
		assertThat(this.mvc.get().uri("/api/v2/artists")).hasStatus(400)
			.bodyJson()
			.extractingPath("$.error.message")
			.asString()
			.contains("Unsupported API version");
		assertThat(this.mvc.get().uri("/api/1/artists/the-beatles")).as("1 and v1 are the same version").hasStatusOk();
	}

	@Test
	void wrongMethodAndMediaType() {
		String token = newUser("methods").token();
		assertThat(this.mvc.put().uri(API + "/artists/the-beatles").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.hasStatus(405)
			.containsHeader(HttpHeaders.ALLOW)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("method_not_allowed");
		assertThat(this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.TEXT_PLAIN)
			.content("hello")).hasStatus(415).bodyJson().extractingPath("$.error.code").isEqualTo("unsupported_media_type");
	}

	@Test
	void malformedAndInvalidBodies() {
		String token = newUser("bodies").token();
		assertThat(this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": ")).hasStatus(400).bodyJson().extractingPath("$.error.code").isEqualTo("bad_request");
		assertThat(this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "", "track_ids": [0]}""")).hasStatus(422).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.error.code").isEqualTo("validation_failed");
			assertThat(json).extractingPath("$.error.details[*].field").asArray().contains("name", "track_ids[0]");
		});
		assertThat(this.mvc.get().uri(API + "/playlists/not-a-number")).hasStatus(400)
			.bodyJson()
			.extractingPath("$.error.message")
			.isEqualTo("Invalid value 'not-a-number' for 'id'.");
		assertThat(this.mvc.get().uri(API + "/artists?limit=0")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.details[0].field")
			.isEqualTo("limit");
	}

}
