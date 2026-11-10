package com.oggmont.music.library;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The library (liked tracks, saved albums) and follows. PUT and DELETE are idempotent.
 */
class LibraryAndFollowIT extends IntegrationTest {

	@Test
	void likingIsIdempotentAndContainsAnswersForEachTrack() {
		TestUser user = newUser("likes");
		assertThat(put(user, "/me/tracks/3674")).hasStatus(204);
		assertThat(put(user, "/me/tracks/3674")).hasStatus(204);
		assertThat(put(user, "/me/tracks/999999")).hasStatus(404);
		assertThat(get(user, "/me/tracks/contains?ids=3674,3668,4199")).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					{"3674": true, "3668": false, "4199": false}""");
		assertThat(delete(user, "/me/tracks/3674")).hasStatus(204);
		assertThat(delete(user, "/me/tracks/3674")).hasStatus(204);
		assertThat(get(user, "/me/tracks/contains?ids=3674")).bodyJson().extractingPath("$['3674']").isEqualTo(false);
		assertThat(get(user, "/me/tracks/contains?ids=")).hasStatus4xxClientError();
	}

	@Test
	void likedTracksAreNewestFirstWithAKeysetCursor() {
		TestUser user = newUser("keyset");
		for (int track : List.of(3674, 3668, 4199)) {
			assertThat(put(user, "/me/tracks/" + track)).hasStatus(204);
		}
		MvcTestResult first = get(user, "/me/tracks?limit=2").exchange();
		assertThat(first).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(3);
		List<Integer> firstIds = read(first, "$.items[*].track.track_id");
		assertThat(firstIds).containsExactly(4199, 3668);

		String before = read(first, "$.items[1].saved_at");
		MvcTestResult next = get(user, "/me/tracks?limit=2&before=" + before + "&before_id=3668").exchange();
		List<Integer> nextIds = read(next, "$.items[*].track.track_id");
		assertThat(nextIds).containsExactly(3674);
	}

	@Test
	void savedAlbums() {
		TestUser user = newUser("albums");
		assertThat(put(user, "/me/albums/359")).hasStatus(204);
		assertThat(put(user, "/me/albums/999999")).hasStatus(404);
		assertThat(get(user, "/me/albums")).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.total").isEqualTo(1);
			assertThat(json).extractingPath("$.items[0].album.title").isEqualTo("Abbey Road");
		});
		assertThat(this.mvc.get().uri(API + "/albums/359").header(HttpHeaders.AUTHORIZATION, user.bearer()))
			.bodyJson()
			.extractingPath("$.is_saved_by_me")
			.isEqualTo(true);
		assertThat(delete(user, "/me/albums/359")).hasStatus(204);
	}

	@Test
	void followingAnArtistUpdatesItsFollowerCount() {
		TestUser user = newUser("fan");
		int before = followers("/artists/daft-punk");
		assertThat(put(user, "/me/following/artists/daft-punk")).hasStatus(204);
		assertThat(put(user, "/me/following/artists/daft-punk")).hasStatus(204);
		assertThat(followers("/artists/daft-punk")).isEqualTo(before + 1);
		assertThat(get(user, "/me/following/artists")).bodyJson()
			.extractingPath("$.items[0].artist.slug")
			.isEqualTo("daft-punk");
		assertThat(delete(user, "/me/following/artists/daft-punk")).hasStatus(204);
		assertThat(followers("/artists/daft-punk")).isEqualTo(before);
		assertThat(put(user, "/me/following/artists/no-such-artist")).hasStatus(404);
	}

	@Test
	void followingUsers() {
		TestUser a = newUser("follower");
		TestUser b = newUser("followed");
		assertThat(put(a, "/me/following/users/" + b.username())).hasStatus(204);
		assertThat(this.mvc.get().uri(API + "/users/{u}", b.username()).header(HttpHeaders.AUTHORIZATION, a.bearer()))
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.follower_count").isEqualTo(1);
				assertThat(json).extractingPath("$.is_followed_by_me").isEqualTo(true);
			});
		assertThat(this.mvc.get().uri(API + "/users/{u}/followers", b.username())).bodyJson()
			.extractingPath("$.items[0].username")
			.isEqualTo(a.username());
		assertThat(this.mvc.get().uri(API + "/users/{u}/following", a.username())).bodyJson()
			.extractingPath("$.items[0].username")
			.isEqualTo(b.username());
		assertThat(put(a, "/me/following/users/" + a.username())).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("cannot_follow_self");
	}

	@Test
	void followingPlaylistsRespectsVisibility() {
		TestUser user = newUser("listfan");
		assertThat(put(user, "/me/following/playlists/23")).hasStatusOk()
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.is_favorite").isEqualTo(true);
				assertThat(json).extractingPath("$.visibility").isEqualTo("public");
				assertThat(json).extractingPath("$.my_favorite_count").isEqualTo(1);
			});
		assertThat(put(user, "/me/following/playlists/21")).as("someone else's private playlist").hasStatus(404);
		assertThat(delete(user, "/me/following/playlists/21")).as("a private playlist you never favorited")
			.hasStatus(404);
		assertThat(get(user, "/me/following/playlists")).bodyJson()
			.extractingPath("$.items[*].playlist.name")
			.asArray()
			.containsExactly("Daft Punk Essentials");
		List<String> followerNames = read(this.mvc.get().uri(API + "/playlists/23/followers?limit=100").exchange(),
				"$.items[*].username");
		assertThat(followerNames).contains(user.username());
		assertThat(this.mvc.get().uri(API + "/playlists/21/followers")).hasStatus(404);
	}

	private int followers(String path) {
		return read(this.mvc.get().uri(API + path).exchange(), "$.follower_count");
	}

	private org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder get(TestUser user,
			String path) {
		return this.mvc.get().uri(API + path).header(HttpHeaders.AUTHORIZATION, user.bearer());
	}

	private MvcTestResult put(TestUser user, String path) {
		return this.mvc.put().uri(API + path).header(HttpHeaders.AUTHORIZATION, user.bearer()).exchange();
	}

	private MvcTestResult delete(TestUser user, String path) {
		return this.mvc.delete().uri(API + path).header(HttpHeaders.AUTHORIZATION, user.bearer()).exchange();
	}

}
