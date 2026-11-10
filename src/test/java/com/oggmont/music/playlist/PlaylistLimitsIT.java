package com.oggmont.music.playlist;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The web UI's limits (20 owned playlists, 100 tracks per playlist), derived visibility,
 * the add menu's {@code /me/playlists} parameters and random playlists.
 */
class PlaylistLimitsIT extends IntegrationTest {

	private static final int SOMETHING = 3669;

	private static final int COME_TOGETHER = 3668;

	@Test
	void visibilityCantBeSent() {
		TestUser owner = newUser("novis");
		assertThat(this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Loud", "visibility": "public"}""")).hasStatus(422).bodyJson().satisfies((json) -> {
				assertThat(json).extractingPath("$.error.code").isEqualTo("visibility_follows_favorites");
				assertThat(json).extractingPath("$.error.details[0].field").isEqualTo("visibility");
			});
		int id = createPlaylist(owner.token(), "Quiet");
		assertThatThrownBy(() -> this.jdbc.sql("UPDATE playlist SET visibility = 'public' WHERE playlist_id = :id")
			.param("id", id)
			.update()).as("the database refuses it too")
			.hasMessageContaining("visibility follows favorites");
	}

	@Test
	void aPlaylistHoldsAtMostOneHundredTracks() {
		TestUser owner = newUser("hundred");
		List<Integer> tracks = this.jdbc.sql("SELECT track_id FROM track ORDER BY track_id LIMIT 101")
			.query(Integer.class)
			.list();
		assertThat(create(owner, "Too big", tracks)).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("playlist_full");

		MvcTestResult created = create(owner, "Nearly full", tracks.subList(0, 98));
		assertThat(created).hasStatus(201);
		int id = read(created, "$.playlist_id");
		assertThat(addTracks(owner, id, tracks.subList(98, 101))).hasStatus(422).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.error.code").isEqualTo("playlist_full");
			assertThat(json).extractingPath("$.error.message")
				.isEqualTo("Nearly full has 98 of 100 tracks; 2 more fit, not 3.");
			assertThat(json).extractingPath("$.error.details[0].message").isEqualTo("at most 2");
		});
		assertThat(addTracks(owner, id, tracks.subList(98, 100))).hasStatus(201);
		assertThat(addTracks(owner, id, List.of(SOMETHING))).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.message")
			.isEqualTo("Nearly full has 100 of 100 tracks; it's full.");

		// The Beatles: Original Albums (193 tracks) is older than the limit: it can't be copied whole.
		assertThat(this.mvc.post().uri(API + "/playlists/19/copy").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("playlist_full");
	}

	@Test
	void aUserOwnsAtMostTwentyPlaylists() {
		TestUser owner = newUser("twenty");
		for (int i = 1; i <= 20; i++) {
			createPlaylist(owner.token(), "List " + i);
		}
		assertThat(create(owner, "Twenty-one", List.of())).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("playlist_limit_reached");
		assertThat(this.mvc.post().uri(API + "/playlists/20/copy").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.as("a copy counts too")
			.hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("playlist_limit_reached");
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, owner.bearer())).bodyJson()
			.extractingPath("$.owned_playlist_count")
			.isEqualTo(20);
	}

	@Test
	void myPlaylistsServeTheAddMenu() {
		TestUser owner = newUser("menu");
		int zulu = createPlaylist(owner.token(), "Zulu", SOMETHING);
		int echo = createPublicPlaylist(owner.token(), "écho");
		int alpha = createPlaylist(owner.token(), "Alpha");
		assertThat(addTracks(owner, zulu, List.of(COME_TOGETHER))).hasStatus(201); // Zulu: used last

		assertThat(ids(owner, "")).as("newest first by default").containsExactly(alpha, echo, zulu);
		assertThat(ids(owner, "?sort=name")).as("A-Z, ignoring accents").containsExactly(alpha, echo, zulu);
		assertThat(ids(owner, "?sort=last_added")).as("last used first, then newest").containsExactly(zulu, alpha, echo);

		assertThat(this.mvc.get()
			.uri(API + "/me/playlists?role=owner&sort=name&contains_track=" + SOMETHING)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())).hasStatusOk().bodyJson().satisfies((json) -> {
				assertThat(json).extractingPath("$.total").isEqualTo(3);
				assertThat(json).extractingPath("$.items[*].contains_track").asArray().containsExactly(false, false, true);
				assertThat(json).extractingPath("$.items[*].is_favorite").asArray().containsExactly(false, true, false);
				assertThat(json).extractingPath("$.items[*].my_role").asArray().containsOnly("owner");
				assertThat(json).extractingPath("$.items[0].last_added_at").isNull();
				assertThat(json).extractingPath("$.items[2].last_added_at").isNotNull();
				assertThat(json).extractingPath("$.items[1].visibility").isEqualTo("public");
				assertThat(json).extractingPath("$.items[2].track_count").isEqualTo(2);
			});
		assertThat(this.mvc.get().uri(API + "/me/playlists").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.bodyJson()
			.doesNotHavePath("$.items[0].contains_track");
		assertThat(this.mvc.get().uri(API + "/me/playlists?sort=size").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.hasStatus(422);
		assertThat(this.mvc.get().uri(API + "/me/playlists?role=boss").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.hasStatus(422);
	}

	@Test
	void randomPlaylistsArePublicOnesByOthersNotYetFavorited() {
		TestUser me = newUser("discover");
		int mine = createPublicPlaylist(me.token(), "My own");
		assertThat(favorite(me.token(), 23)).hasStatusOk(); // Daft Punk Essentials

		for (int i = 0; i < 5; i++) {
			MvcTestResult result = this.mvc.get()
				.uri(API + "/playlists/random?limit=100")
				.header(HttpHeaders.AUTHORIZATION, me.bearer())
				.exchange();
			assertThat(result).hasStatusOk().bodyJson().doesNotHavePath("$.total");
			List<Integer> ids = read(result, "$.items[*].playlist_id");
			List<String> visibilities = read(result, "$.items[*].visibility");
			assertThat(ids).doesNotContain(mine, 23, 21, 22).doesNotHaveDuplicates();
			assertThat(visibilities).containsOnly("public");
		}
		List<Integer> anonymous = read(this.mvc.get().uri(API + "/playlists/random?limit=100&exclude=20,24").exchange(),
				"$.items[*].playlist_id");
		assertThat(anonymous).contains(mine, 23).doesNotContain(20, 24, 21);
		assertThat(this.mvc.get().uri(API + "/playlists/random?limit=0")).hasStatus(422);
	}

	private List<Integer> ids(TestUser user, String query) {
		return read(this.mvc.get().uri(API + "/me/playlists" + query).header(HttpHeaders.AUTHORIZATION, user.bearer())
			.exchange(), "$.items[*].playlist_id");
	}

	private MvcTestResult create(TestUser owner, String name, List<Integer> trackIds) {
		return this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "%s", "track_ids": [%s]}""".formatted(name, join(trackIds)))
			.exchange();
	}

	private MvcTestResult addTracks(TestUser user, int id, List<Integer> trackIds) {
		return this.mvc.post()
			.uri(API + "/playlists/{id}/tracks", id)
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.header(HttpHeaders.IF_MATCH, "*")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"track_ids": [%s]}""".formatted(join(trackIds)))
			.exchange();
	}

	private static String join(List<Integer> ids) {
		return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

}
