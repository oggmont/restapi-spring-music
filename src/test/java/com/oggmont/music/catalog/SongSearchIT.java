package com.oggmont.music.catalog;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Songs screen: song search ({@code GET /tracks?q=}) and random songs
 * ({@code GET /tracks/random}).
 */
class SongSearchIT extends IntegrationTest {

	@Test
	void searchFindsTracksByNameArtistOrAlbumIgnoringAccents() {
		MvcTestResult daftPunk = this.mvc.get().uri(API + "/tracks?q=daft punk&limit=5").exchange();
		assertThat(daftPunk).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.query").isEqualTo("daft punk");
			assertThat(json).extractingPath("$.limit").isEqualTo(5);
			assertThat(json).extractingPath("$.items.length()").isEqualTo(5);
			assertThat(json).extractingPath("$.items[0].track_id").isNotNull();
			assertThat(json).extractingPath("$.items[0].duration").isNotNull();
			assertThat(json).extractingPath("$.items[0].album.title").isNotNull();
			assertThat(json).extractingPath("$.items[0].score").isNotNull();
			assertThat(json).doesNotHavePath("$.items[0].is_liked");
		});
		int total = read(daftPunk, "$.total");
		List<List<String>> artists = read(daftPunk, "$.items[*].artists");
		assertThat(artists).allSatisfy((names) -> assertThat(names).contains("Daft Punk"));
		assertThat(read(this.mvc.get().uri(API + "/tracks?q=punk daft").exchange(), "$.total").toString())
			.as("words in any order")
			.isEqualTo(String.valueOf(total));

		List<List<String>> jose = read(this.mvc.get().uri(API + "/tracks?q=jose&limit=100").exchange(), "$.items[*].artists");
		assertThat(jose).anySatisfy((names) -> assertThat(names).contains("José José"));

		List<Double> scores = read(this.mvc.get().uri(API + "/tracks?q=come together").exchange(), "$.items[*].score");
		assertThat(scores).isSortedAccordingTo((a, b) -> Double.compare(b, a));
		assertThat(read(this.mvc.get().uri(API + "/tracks?q=come together").exchange(), "$.items[0].name").toString())
			.isEqualTo("Come Together");

		assertThat(this.mvc.get().uri(API + "/tracks?q=daft punk&offset=10000")).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.items.length()").isEqualTo(0);
			assertThat(json).extractingPath("$.total").isEqualTo(total);
		});
	}

	@Test
	void searchNeedsTwoCharactersAndReportsLikes() {
		assertThat(this.mvc.get().uri(API + "/tracks?q= a ")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("query_too_short");
		assertThat(this.mvc.get().uri(API + "/tracks")).hasStatus(422);
		assertThat(this.mvc.get().uri(API + "/tracks?q={q}", "%_%")).as("LIKE wildcards are literal").hasStatusOk()
			.bodyJson()
			.extractingPath("$.total")
			.isEqualTo(0);

		TestUser user = newUser("searcher");
		assertThat(this.mvc.put().uri(API + "/me/tracks/3668").header(HttpHeaders.AUTHORIZATION, user.bearer()))
			.hasStatus(204);
		assertThat(this.mvc.get().uri(API + "/tracks?q=come together&limit=1").header(HttpHeaders.AUTHORIZATION,
				user.bearer()))
			.bodyJson()
			.extractingPath("$.items[0].is_liked")
			.isNotNull();
	}

	@Test
	void randomTracksAreANewSampleEachTime() {
		MvcTestResult first = this.mvc.get().uri(API + "/tracks/random?limit=20").exchange();
		assertThat(first).hasStatusOk().bodyJson().doesNotHavePath("$.total");
		List<Integer> ids = read(first, "$.items[*].track_id");
		assertThat(ids).hasSize(20).doesNotHaveDuplicates();

		String exclude = String.join(",", ids.stream().map(String::valueOf).toList());
		List<Integer> more = read(this.mvc.get().uri(API + "/tracks/random?limit=20&exclude=" + exclude).exchange(),
				"$.items[*].track_id");
		assertThat(more).hasSize(20).doesNotContainAnyElementsOf(ids);

		assertThat(this.mvc.get().uri(API + "/tracks/random")).bodyJson()
			.extractingPath("$.items.length()")
			.isEqualTo(20);
		assertThat(this.mvc.get().uri(API + "/tracks/random?limit=101")).hasStatus(422);
	}

}
