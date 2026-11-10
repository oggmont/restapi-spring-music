package com.oggmont.music.follow;

import com.oggmont.music.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Favorites (playlist follows): at most 5 per user, the atomic swap, and visibility that
 * follows favorites (API-Design.md, "Favorites, visibility and limits").
 */
class FavoritesIT extends IntegrationTest {

	private static final int SOMETHING = 3669;

	@Test
	void aPlaylistIsPublicWhileAnyoneHasItAsAFavorite() {
		TestUser owner = newUser("favowner");
		TestUser fan = newUser("favfan");
		int id = createPlaylist(owner.token(), "Sunday Morning", SOMETHING);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, fan.bearer()))
			.as("new playlists are private")
			.hasStatus(404);

		assertThat(favorite(owner.token(), id)).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.playlist_id").isEqualTo(id);
			assertThat(json).extractingPath("$.is_favorite").isEqualTo(true);
			assertThat(json).extractingPath("$.visibility").isEqualTo("public");
			assertThat(json).extractingPath("$.follower_count").isEqualTo(1);
			assertThat(json).extractingPath("$.my_favorite_count").isEqualTo(1);
			assertThat(json).doesNotHavePath("$.replaced");
		});
		assertThat(favorite(owner.token(), id)).as("idempotent").hasStatusOk().bodyJson()
			.extractingPath("$.follower_count")
			.isEqualTo(1);
		assertThat(favorite(fan.token(), id)).hasStatusOk().bodyJson().extractingPath("$.follower_count").isEqualTo(2);

		// The owner unfavorites: off the owner's profile, but still public for the fan.
		assertThat(unfavorite(owner.token(), id)).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.is_favorite").isEqualTo(false);
			assertThat(json).extractingPath("$.visibility").isEqualTo("public");
			assertThat(json).extractingPath("$.follower_count").isEqualTo(1);
		});
		assertThat(favoriteNames(owner.username())).isEmpty();
		assertThat(favoriteNames(fan.username())).containsExactly("Sunday Morning");

		// The last favorite goes: private, and the fan can't open it any more.
		assertThat(unfavorite(fan.token(), id)).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.visibility").isEqualTo("private");
			assertThat(json).extractingPath("$.follower_count").isEqualTo(0);
			assertThat(json).extractingPath("$.my_favorite_count").isEqualTo(0);
		});
		assertThat(unfavorite(fan.token(), id)).as("still idempotent, though the fan can't view it").hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, fan.bearer()))
			.hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.visibility")
			.isEqualTo("private");
	}

	@Test
	void atMostFiveFavoritesWithAnAtomicSwap() {
		TestUser user = newUser("five");
		List<Integer> ids = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			ids.add(createPlaylist(user.token(), "Mine " + i, SOMETHING));
		}
		for (int i = 0; i < 5; i++) {
			assertThat(favorite(user.token(), ids.get(i))).hasStatusOk();
		}
		assertThat(favorite(user.token(), ids.get(5))).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("favorite_limit_reached");
		assertThat(favorite(user.token(), ids.get(0))).as("re-favoriting one of the five").hasStatusOk();

		assertThat(swap(user, ids.get(5), 999999)).as("replaces must be a favorite").hasStatus(404);
		assertThat(me(user)).bodyJson().extractingPath("$.favorite_count").isEqualTo(5);

		assertThat(swap(user, ids.get(5), ids.get(0))).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.playlist_id").isEqualTo(ids.get(5));
			assertThat(json).extractingPath("$.visibility").isEqualTo("public");
			assertThat(json).extractingPath("$.my_favorite_count").isEqualTo(5);
			assertThat(json).extractingPath("$.replaced.playlist_id").isEqualTo(ids.get(0));
			assertThat(json).extractingPath("$.replaced.visibility").isEqualTo("private");
			assertThat(json).extractingPath("$.replaced.follower_count").isEqualTo(0);
		});
		assertThat(me(user)).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.favorite_count").isEqualTo(5);
			assertThat(json).extractingPath("$.owned_playlist_count").isEqualTo(6);
		});
	}

	@Test
	void favoritesAreListedAToZOrByDate() {
		TestUser user = newUser("sorter");
		int zeta = createPublicPlaylist(user.token(), "Zeta", SOMETHING);
		int album = createPublicPlaylist(user.token(), "Álbum", SOMETHING);
		int beta = createPublicPlaylist(user.token(), "beta", SOMETHING);

		assertThat(favoriteNames(user.username())).containsExactly("Zeta", "Álbum", "beta");
		List<String> byName = read(this.mvc.get().uri(API + "/users/{u}/favorites?sort=name", user.username()).exchange(),
				"$.items[*].name");
		assertThat(byName).as("A-Z, ignoring accents and case").containsExactly("Álbum", "beta", "Zeta");
		assertThat(this.mvc.get().uri(API + "/users/{u}/favorites", user.username())).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.items[0].playlist_id").isEqualTo(zeta);
			assertThat(json).extractingPath("$.items[0].owner.username").isEqualTo(user.username());
			assertThat(json).extractingPath("$.items[0].favorited_at").isNotNull();
			assertThat(json).extractingPath("$.items[0].track_count").isEqualTo(1);
		});
		List<Integer> mine = read(this.mvc.get()
			.uri(API + "/me/following/playlists?sort=name")
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.exchange(), "$.items[*].playlist.playlist_id");
		assertThat(mine).containsExactly(album, beta, zeta);
		assertThat(this.mvc.get().uri(API + "/users/{u}/favorites?sort=size", user.username())).hasStatus(422);
		assertThat(this.mvc.get().uri(API + "/users/nobody.here/favorites")).hasStatus(404);
	}

	@Test
	void seedFavoritesKeepTheirPlaylistsPublic() {
		List<String> kenji = read(this.mvc.get().uri(API + "/users/kenji.watanabe/favorites?sort=name").exchange(),
				"$.items[*].name");
		assertThat(kenji).containsExactly("Daft Punk Essentials", "Road Trip", "The Beatles: Original Albums");
		List<String> visibilities = read(this.mvc.get().uri(API + "/users/kenji.watanabe/favorites").exchange(),
				"$.items[*].visibility");
		assertThat(visibilities).containsOnly("public");
	}

	private MvcTestResult swap(TestUser user, int playlistId, int replaces) {
		return this.mvc.put()
			.uri(API + "/me/following/playlists/{id}", playlistId)
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"replaces": %d}""".formatted(replaces))
			.exchange();
	}

	private MvcTestResult me(TestUser user) {
		return this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, user.bearer()).exchange();
	}

	private List<String> favoriteNames(String username) {
		return read(this.mvc.get().uri(API + "/users/{u}/favorites", username).exchange(), "$.items[*].name");
	}

}
