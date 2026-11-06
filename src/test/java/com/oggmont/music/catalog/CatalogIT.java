package com.oggmont.music.catalog;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog reads, checked against the seed data (the same examples API-Design.md uses).
 */
class CatalogIT extends IntegrationTest {

	@Test
	void artistPage() {
		assertThat(this.mvc.get().uri(API + "/artists/the-beatles")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.artist_id").isEqualTo(276);
			assertThat(json).extractingPath("$.artist_type").isEqualTo("group");
			assertThat(json).extractingPath("$.city").isEqualTo("Liverpool");
			assertThat(json).extractingPath("$.album_count").isEqualTo(20);
			assertThat(json).extractingPath("$.track_count").isEqualTo(462);
			assertThat(json).doesNotHavePath("$.is_followed_by_me");
		});
		String token = newUser("catalog").token();
		assertThat(this.mvc.get().uri(API + "/artists/the-beatles").header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.bodyJson()
			.extractingPath("$.is_followed_by_me")
			.isEqualTo(false);
	}

	@Test
	void artistsCanBeFilteredAndSorted() {
		MvcTestResult result = this.mvc.get().uri(API + "/artists?type=group&country=gb&genre=Rock&sort=followers").exchange();
		assertThat(result).hasStatusOk();
		List<String> slugs = read(result, "$.items[*].slug");
		assertThat(slugs).contains("the-beatles", "pink-floyd");
		List<Integer> followers = read(result, "$.items[*].follower_count");
		assertThat(followers).isSortedAccordingTo((a, b) -> Integer.compare(b, a));

		assertThat(this.mvc.get().uri(API + "/artists?limit=5&offset=5")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.limit").isEqualTo(5);
			assertThat(json).extractingPath("$.offset").isEqualTo(5);
			assertThat(json).extractingPath("$.items.length()").isEqualTo(5);
		});
		assertThat(this.mvc.get().uri(API + "/artists?sort=popularity")).hasStatus(422);
	}

	@Test
	void discographyIncludesTheArtistsRoleInOrderOfRelease() {
		MvcTestResult result = this.mvc.get().uri(API + "/artists/the-beatles/albums?limit=100").exchange();
		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(20);
		assertThat(read(result, "$.items[0].title").toString()).isEqualTo("Please Please Me");
		List<String> roles = read(result, "$.items[*].role");
		assertThat(roles).containsOnly("main");
		MvcTestResult studio = this.mvc.get().uri(API + "/artists/the-beatles/albums?type=studio&limit=100").exchange();
		List<String> types = read(studio, "$.items[*].album_type");
		assertThat(types).isNotEmpty().containsOnly("studio");
	}

	@Test
	void bandLineUpHasOneRowPerStintAndSoloArtistsTheirPerson() {
		MvcTestResult floyd = this.mvc.get().uri(API + "/artists/pink-floyd/members").exchange();
		List<String> names = read(floyd, "$.items[*].name");
		assertThat(names).filteredOn("Richard Wright"::equals).hasSize(2);
		assertThat(this.mvc.get().uri(API + "/artists/jose-jose/members")).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.total").isEqualTo(1);
			assertThat(json).extractingPath("$.items[0].name").isEqualTo("José José");
		});
	}

	@Test
	void albumByArtistAndAlbumSlug() {
		assertThat(this.mvc.get().uri(API + "/artists/the-beatles/albums/abbey-road")).hasStatusOk()
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.album_id").isEqualTo(359);
				assertThat(json).extractingPath("$.release_date").isEqualTo("1969-09-26");
				assertThat(json).extractingPath("$.label.slug").isEqualTo("apple");
				assertThat(json).extractingPath("$.artists[0].role").isEqualTo("main");
				assertThat(json).extractingPath("$.track_count").isEqualTo(17);
				assertThat(json).extractingPath("$.duration_ms").isEqualTo(2822000);
			});
		assertThat(this.mvc.get().uri(API + "/artists/the-beatles/albums/no-such-album")).hasStatus(404);
	}

	@Test
	void albumTrackListingIsInDiscAndTrackOrder() {
		assertThat(this.mvc.get().uri(API + "/albums/359/tracks")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.total").isEqualTo(17);
			assertThat(json).extractingPath("$.items[0].name").isEqualTo("Come Together");
			assertThat(json).extractingPath("$.items[0].duration").isEqualTo("4:20");
			assertThat(json).extractingPath("$.items[0].composer").isEqualTo("John Lennon, Paul McCartney");
			assertThat(json).extractingPath("$.items[1].track_number").isEqualTo(2);
		});
	}

	@Test
	void trackSongAndRecordings() {
		assertThat(this.mvc.get().uri(API + "/tracks/3668")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.song.song_id").isEqualTo(3281);
			assertThat(json).extractingPath("$.album.title").isEqualTo("Abbey Road");
			assertThat(json).extractingPath("$.artist.slug").isEqualTo("the-beatles");
			assertThat(json).extractingPath("$.genre.name").isEqualTo("Rock");
		});
		assertThat(this.mvc.get().uri(API + "/songs/3282/recordings")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.song.title").isEqualTo("Something");
			assertThat(json).extractingPath("$.song.credits[0].name").isEqualTo("George Harrison");
			assertThat(json).extractingPath("$.total").isEqualTo(4);
			assertThat(json).extractingPath("$.items[0].album_title").isEqualTo("Abbey Road");
		});
	}

	@Test
	void creditsShowTheNameAsPrinted() {
		// Ringo Starr's songs credit his legal name.
		MvcTestResult song = this.mvc.get().uri(API + "/songs/{id}", songIdOf("Octopus's Garden")).exchange();
		assertThat(song).hasStatusOk().bodyJson().extractingPath("$.credits[0].credited_as").isEqualTo("Richard Starkey");
	}

	@Test
	void personPageListsBandsAndCredits() {
		assertThat(this.mvc.get().uri(API + "/people/1")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("John Lennon");
			assertThat(json).extractingPath("$.bands[0].artist.slug").isEqualTo("the-beatles");
			assertThat(json).extractingPath("$.credits.length()").asNumber().satisfies((n) -> assertThat(n.intValue()).isPositive());
		});
	}

	@Test
	void genreTreeAndAlbumsIncludingSubgenres() {
		MvcTestResult genres = this.mvc.get().uri(API + "/genres").exchange();
		assertThat(genres).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
		List<String> rockChildren = read(genres, "$[?(@.name == 'Rock')].children[*].name");
		assertThat(rockChildren).isNotEmpty();

		MvcTestResult all = this.mvc.get().uri(API + "/genres/1/albums").exchange();
		MvcTestResult primary = this.mvc.get().uri(API + "/genres/1/albums?primary_only=true").exchange();
		int allTotal = read(all, "$.total");
		int primaryTotal = read(primary, "$.total");
		assertThat(allTotal).isGreaterThanOrEqualTo(primaryTotal).isPositive();
	}

	@Test
	void labelWithItsAlbums() {
		assertThat(this.mvc.get().uri(API + "/labels/apple")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Apple");
			assertThat(json).extractingPath("$.albums.items[*].title").asArray().contains("Abbey Road");
		});
	}

	@Test
	void unknownCatalogItemsAre404() {
		for (String path : List.of("/artists/no-such-artist", "/artists/no-such-artist/albums", "/albums/999999",
				"/albums/999999/tracks", "/tracks/999999", "/songs/999999", "/songs/999999/recordings",
				"/people/999999", "/genres/999999/albums", "/labels/no-such-label")) {
			assertThat(this.mvc.get().uri(API + path)).as(path)
				.hasStatus(404)
				.bodyJson()
				.extractingPath("$.error.code")
				.isEqualTo("not_found");
		}
	}

	private int songIdOf(String title) {
		return this.jdbc.sql("SELECT song_id FROM song WHERE title = :title ORDER BY song_id LIMIT 1")
			.param("title", title)
			.query(Integer.class)
			.single();
	}

}
