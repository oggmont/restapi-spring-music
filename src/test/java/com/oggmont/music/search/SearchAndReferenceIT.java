package com.oggmont.music.search;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search box and the reference data.
 */
class SearchAndReferenceIT extends IntegrationTest {

	@Test
	void searchFiltersByKind() {
		MvcTestResult result = this.mvc.get().uri(API + "/search?q=come together&types=song,track&limit=5").exchange();
		assertThat(result).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.query").isEqualTo("come together");
			assertThat(json).extractingPath("$.items[0].kind").isEqualTo("song");
			assertThat(json).extractingPath("$.items[0].id").isEqualTo(3281);
		});
		List<String> kinds = read(result, "$.items[*].kind");
		assertThat(kinds).hasSize(5).containsOnly("song", "track");
	}

	@Test
	void searchIsAccentInsensitive() {
		assertThat(this.mvc.get().uri(API + "/search?q=jose&types=artist")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.items[0].name").isEqualTo("José José");
			assertThat(json).extractingPath("$.items[0].slug").isEqualTo("jose-jose");
		});
	}

	@Test
	void searchRejectsBadInput() {
		assertThat(this.mvc.get().uri(API + "/search")).hasStatus(400);
		assertThat(this.mvc.get().uri(API + "/search?q=   ")).hasStatus(422);
		assertThat(this.mvc.get().uri(API + "/search?q=x&types=band")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_type");
		assertThat(this.mvc.get().uri(API + "/search?q=x&limit=1000")).hasStatus(422);
	}

	@Test
	void referenceDataIsCacheable() {
		assertThat(this.mvc.get().uri(API + "/reference/playlist-visibilities")).hasStatusOk()
			.hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400, public")
			.bodyJson()
			.extractingPath("$[*].code")
			.asArray()
			.containsExactlyInAnyOrder("public", "unlisted", "private");
		assertThat(this.mvc.get().uri(API + "/reference/countries")).bodyJson().extractingPath("$.length()").isEqualTo(249);
		assertThat(this.mvc.get().uri(API + "/reference/playlist-roles")).bodyJson()
			.extractingPath("$[?(@.code == 'editor')].can_edit")
			.asArray()
			.containsExactly(true);
		assertThat(this.mvc.get().uri(API + "/reference/unknown")).hasStatus(404);
	}

}
