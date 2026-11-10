package com.oggmont.music.playlist;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Playlists, their track lists (with optimistic locking through ETag / If-Match),
 * visibility, share links, copies and collaborators.
 */
class PlaylistIT extends IntegrationTest {

	private static final int HERE_COMES_THE_SUN = 3674;

	private static final int SOMETHING = 3669;

	private static final int WISH_YOU_WERE_HERE = 4037;

	private static final int HEY_JUDE = 3735;

	private static final int COME_TOGETHER = 3668;

	@Test
	void createAnswersWithLocationEtagAndTheShareTokenForTheOwner() {
		TestUser owner = newUser("create");
		MvcTestResult result = this.mvc.post()
			.uri(API + "/playlists")
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Sunday Morning", "description": "Coffee and vinyl.", "track_ids": [3674, 3669, 4037]}""")
			.exchange();
		assertThat(result).hasStatus(201).hasHeader(HttpHeaders.ETAG, "\"2\"").bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.visibility").isEqualTo("private");
			assertThat(json).extractingPath("$.track_count").isEqualTo(3);
			assertThat(json).extractingPath("$.duration_ms").isEqualTo(685000);
			assertThat(json).extractingPath("$.version").isEqualTo(2);
			assertThat(json).extractingPath("$.owner.username").isEqualTo(owner.username());
			assertThat(json).extractingPath("$.my_access.can_edit").isEqualTo(true);
			assertThat(json).hasPath("$.share_token");
		});
		int id = read(result, "$.playlist_id");
		assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/playlists/" + id);
		assertThat(entries(owner.token(), id)).containsExactly(HERE_COMES_THE_SUN, SOMETHING, WISH_YOU_WERE_HERE);
	}

	@Test
	void visibilityDecidesWhoCanOpenAPlaylist() {
		TestUser owner = newUser("vis");
		TestUser stranger = newUser("stranger");
		int privateId = createPlaylist(owner.token(), "Mine", SOMETHING);
		int publicId = createPublicPlaylist(owner.token(), "For everyone", SOMETHING);

		assertThat(this.mvc.get().uri(API + "/playlists/{id}", privateId)).hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", privateId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
			.hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}/tracks", privateId)).hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", publicId)).hasStatusOk()
			.hasHeader(HttpHeaders.ETAG, "\"2\"")
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).doesNotHavePath("$.share_token");
				assertThat(json).doesNotHavePath("$.my_access");
			});
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", publicId).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).doesNotHavePath("$.share_token");
				assertThat(json).extractingPath("$.my_access.can_edit").isEqualTo(false);
			});
		List<String> profile = read(this.mvc.get().uri(API + "/users/{u}/playlists", owner.username()).exchange(),
				"$.items[*].name");
		assertThat(profile).containsExactly("For everyone");
	}

	@Test
	void trackListWritesNeedTheCurrentVersion() {
		TestUser owner = newUser("etag");
		int id = createPublicPlaylist(owner.token(), "Versions", SOMETHING);

		assertThat(addTracks(owner, id, null, "[3735]", null)).hasStatus(428)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("precondition_required");
		assertThat(addTracks(owner, id, "\"1\"", "[3735]", null)).hasStatus(412)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("precondition_failed");
		assertThat(addTracks(owner, id, "W/\"2\"", "[3735]", null)).as("weak tags never match").hasStatus(412);

		MvcTestResult added = addTracks(owner, id, "\"2\"", "[3735]", null);
		assertThat(added).hasStatus(201).hasHeader(HttpHeaders.ETAG, "\"3\"").bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.added[0].position").isEqualTo(2);
			assertThat(json).extractingPath("$.added[0].added_by").isEqualTo(owner.username());
		});
		assertThat(addTracks(owner, id, "*", "[3668]", null)).as("If-Match: * matches any version").hasStatus(201);
	}

	@Test
	void addAtAPositionMoveAndRemoveKeepPositionsContiguous() {
		TestUser owner = newUser("order");
		int id = createPublicPlaylist(owner.token(), "Order", HERE_COMES_THE_SUN, SOMETHING, WISH_YOU_WERE_HERE);

		assertThat(addTracks(owner, id, etag(owner, id), "[3735, 3668]", 2)).hasStatus(201);
		assertThat(entries(owner.token(), id)).containsExactly(HERE_COMES_THE_SUN, HEY_JUDE, COME_TOGETHER, SOMETHING,
				WISH_YOU_WERE_HERE);

		assertThat(move(owner, id, 5, 1)).hasStatusOk().bodyJson().extractingPath("$.to").isEqualTo(1);
		assertThat(entries(owner.token(), id)).containsExactly(WISH_YOU_WERE_HERE, HERE_COMES_THE_SUN, HEY_JUDE,
				COME_TOGETHER, SOMETHING);
		assertThat(move(owner, id, 1, 4)).hasStatusOk();
		assertThat(entries(owner.token(), id)).containsExactly(HERE_COMES_THE_SUN, HEY_JUDE, COME_TOGETHER,
				WISH_YOU_WERE_HERE, SOMETHING);

		MvcTestResult removed = this.mvc.delete()
			.uri(API + "/playlists/{id}/tracks/2", id)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.header(HttpHeaders.IF_MATCH, etag(owner, id))
			.exchange();
		assertThat(removed).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.removed.position").isEqualTo(2);
			assertThat(json).extractingPath("$.removed.track_id").isEqualTo(HEY_JUDE);
		});
		assertThat(entries(owner.token(), id)).containsExactly(HERE_COMES_THE_SUN, COME_TOGETHER, WISH_YOU_WERE_HERE,
				SOMETHING);

		assertThat(addTracks(owner, id, etag(owner, id), "[3668]", null)).hasStatus(201);
		MvcTestResult removedAll = this.mvc.delete()
			.uri(API + "/playlists/{id}/tracks?track_id=3668", id)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.header(HttpHeaders.IF_MATCH, etag(owner, id))
			.exchange();
		assertThat(removedAll).hasStatusOk().bodyJson().extractingPath("$.removed").isEqualTo(2);
		assertThat(entries(owner.token(), id)).containsExactly(HERE_COMES_THE_SUN, WISH_YOU_WERE_HERE, SOMETHING);
		List<Integer> positions = read(tracks(owner.token(), id), "$.items[*].position");
		assertThat(positions).containsExactly(1, 2, 3);
	}

	@Test
	void invalidTrackListChangesAreRejected() {
		TestUser owner = newUser("invalid");
		int id = createPublicPlaylist(owner.token(), "Invalid", SOMETHING);
		assertThat(addTracks(owner, id, "*", "[999999]", null)).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("unknown_track");
		assertThat(addTracks(owner, id, "*", "[3735]", 9)).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_position");
		assertThat(move(owner, id, 7, 1)).hasStatus(422);
		assertThat(this.mvc.delete()
			.uri(API + "/playlists/{id}/tracks/5", id)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.header(HttpHeaders.IF_MATCH, "*")).hasStatus(404);
	}

	@Test
	void keysetPagingOfEntries() {
		TestUser owner = newUser("keyset");
		int id = createPublicPlaylist(owner.token(), "Keyset", HERE_COMES_THE_SUN, SOMETHING, WISH_YOU_WERE_HERE,
				HEY_JUDE);
		MvcTestResult page = this.mvc.get().uri(API + "/playlists/{id}/tracks?limit=2&after_position=2", id).exchange();
		assertThat(page).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.total").isEqualTo(4);
			assertThat(json).extractingPath("$.items[*].position").asArray().containsExactly(3, 4);
		});
	}

	@Test
	void patchChangesOnlyTheFieldsSentAndChecksIfMatchWhenGiven() {
		TestUser owner = newUser("patch");
		TestUser other = newUser("notowner");
		int id = createPublicPlaylist(owner.token(), "Before", SOMETHING);

		assertThat(patch(owner, id, null, """
				{"description": "Now with a description"}""")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Before");
			assertThat(json).extractingPath("$.description").isEqualTo("Now with a description");
		});
		assertThat(patch(owner, id, null, """
				{"description": null, "name": "After"}""")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("After");
			assertThat(json).extractingPath("$.description").isNull();
			assertThat(json).extractingPath("$.visibility").isEqualTo("public");
		});
		assertThat(patch(owner, id, "\"1\"", """
				{"name": "Stale"}""")).hasStatus(412);
		assertThat(patch(owner, id, null, """
				{"name": null}""")).hasStatus(422);
		assertThat(patch(owner, id, null, """
				{"visibility": "private"}""")).as("visibility follows favorites").hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("visibility_follows_favorites");
		assertThat(patch(other, id, null, """
				{"name": "Mine now"}""")).hasStatus(403);
	}

	@Test
	void shareLinksOpenPublicPlaylistsUntilRegeneratedOrPrivate() {
		TestUser owner = newUser("share");
		int id = createPublicPlaylist(owner.token(), "Secret mix", SOMETHING, HEY_JUDE);
		String token = read(this.mvc.get().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.exchange(), "$.share_token");

		assertThat(this.mvc.get().uri(API + "/playlists/shared/{token}", token)).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Secret mix");
			assertThat(json).doesNotHavePath("$.share_token");
		});
		assertThat(this.mvc.get().uri(API + "/playlists/shared/{token}/tracks", token)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.total")
			.isEqualTo(2);

		MvcTestResult regenerated = this.mvc.post()
			.uri(API + "/playlists/{id}/share-token", id)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.exchange();
		assertThat(regenerated).hasStatusOk();
		String newToken = read(regenerated, "$.share_token");
		assertThat(newToken).isNotEqualTo(token);
		assertThat(this.mvc.get().uri(API + "/playlists/shared/{token}", token)).hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/shared/{token}", newToken)).hasStatusOk();
		assertThat(this.mvc.get().uri(API + "/playlists/shared/not-a-uuid")).hasStatus(404);

		// A share link stops working while the playlist is private (its last favorite removed).
		assertThat(unfavorite(owner.token(), id)).hasStatusOk().bodyJson().extractingPath("$.visibility").isEqualTo("private");
		assertThat(this.mvc.get().uri(API + "/playlists/shared/{token}", newToken)).hasStatus(404);
	}

	@Test
	void copyingCreatesAPrivatePlaylistOwnedByTheCaller() {
		TestUser copier = newUser("copier");
		MvcTestResult copy = this.mvc.post()
			.uri(API + "/playlists/20/copy")
			.header(HttpHeaders.AUTHORIZATION, copier.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "My Road Trip"}""")
			.exchange();
		assertThat(copy).hasStatus(201).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("My Road Trip");
			assertThat(json).extractingPath("$.visibility").isEqualTo("private");
			assertThat(json).extractingPath("$.owner.username").isEqualTo(copier.username());
			assertThat(json).extractingPath("$.track_count").isEqualTo(7);
		});
		int copyId = read(copy, "$.playlist_id");
		assertThat(entries(copier.token(), copyId)).hasSize(7);
		assertThat(this.mvc.post().uri(API + "/playlists/21/copy").header(HttpHeaders.AUTHORIZATION, copier.bearer()))
			.as("someone else's private playlist")
			.hasStatus(404);
	}

	@Test
	void ownersAndEditorsChangeTracksViewersOnlyLook() {
		TestUser owner = newUser("owner");
		TestUser editor = newUser("editor");
		TestUser viewer = newUser("viewer");
		TestUser stranger = newUser("outsider");
		int id = createPlaylist(owner.token(), "Shared", SOMETHING);

		assertThat(putCollaborator(owner, id, editor.username(), "editor")).hasStatus(201);
		assertThat(putCollaborator(owner, id, viewer.username(), "viewer")).hasStatus(201);
		assertThat(putCollaborator(owner, id, viewer.username(), "viewer")).as("role unchanged").hasStatus(200);

		assertThat(addTracks(editor, id, "*", "[3735]", null)).hasStatus(201);
		assertThat(addTracks(viewer, id, "*", "[3735]", null)).hasStatus(403);
		assertThat(addTracks(stranger, id, "*", "[3735]", null)).hasStatus(404);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.my_access.can_edit")
			.isEqualTo(false);
		assertThat(patch(editor, id, null, """
				{"name": "Editors can't rename"}""")).hasStatus(403);

		MvcTestResult entries = tracks(owner.token(), id);
		assertThat(read(entries, "$.items[1].added_by.username").toString()).isEqualTo(editor.username());

		assertThat(this.mvc.get().uri(API + "/playlists/{id}/collaborators", id).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.total")
			.isEqualTo(2);
		assertThat(putCollaborator(owner, id, owner.username(), "editor")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("owner_as_collaborator");
		assertThat(putCollaborator(owner, id, viewer.username(), "boss")).hasStatus(422);
		assertThat(putCollaborator(editor, id, stranger.username(), "viewer")).as("only the owner").hasStatus(403);

		// A collaborator can leave; then the private playlist is out of reach.
		assertThat(this.mvc.delete()
			.uri(API + "/playlists/{id}/collaborators/{u}", id, viewer.username())
			.header(HttpHeaders.AUTHORIZATION, viewer.bearer())).hasStatus(204);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
			.hasStatus(404);
		assertThat(this.mvc.delete()
			.uri(API + "/playlists/{id}/collaborators/{u}", id, owner.username())
			.header(HttpHeaders.AUTHORIZATION, editor.bearer())).as("editors can't remove others").hasStatus(403);

		List<String> roles = read(this.mvc.get().uri(API + "/me/playlists").header(HttpHeaders.AUTHORIZATION, editor.bearer())
			.exchange(), "$.items[*].my_role");
		assertThat(roles).containsExactly("editor");
		assertThat(this.mvc.get().uri(API + "/me/playlists?role=owner").header(HttpHeaders.AUTHORIZATION, editor.bearer()))
			.bodyJson()
			.extractingPath("$.total")
			.isEqualTo(0);
	}

	@Test
	void roadTripFollowsTheDesignExamples() {
		String kenji = seedUser("kenji.watanabe");
		assertThat(this.mvc.get().uri(API + "/playlists/20").header(HttpHeaders.AUTHORIZATION, bearer(kenji))).hasStatusOk()
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.name").isEqualTo("Road Trip");
				assertThat(json).extractingPath("$.owner.username").isEqualTo("ana.garcia");
				assertThat(json).extractingPath("$.is_collaborative").isEqualTo(true);
				assertThat(json).extractingPath("$.my_access.is_following").isEqualTo(true);
				assertThat(json).extractingPath("$.my_access.can_edit").isEqualTo(false);
			});
		assertThat(this.mvc.post()
			.uri(API + "/playlists/20/tracks")
			.header(HttpHeaders.AUTHORIZATION, bearer(kenji))
			.header(HttpHeaders.IF_MATCH, "*")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"track_ids": [3735]}""")).hasStatus(403);
		assertThat(this.mvc.get().uri(API + "/playlists/20/collaborators").header(HttpHeaders.AUTHORIZATION, bearer(kenji)))
			.as("followers aren't collaborators")
			.hasStatus(403);
	}

	@Test
	void feedShowsEntriesOfFollowedAndSharedPlaylists() {
		String kenji = seedUser("kenji.watanabe");
		assertThat(this.mvc.get().uri(API + "/me/feed?limit=5").header(HttpHeaders.AUTHORIZATION, bearer(kenji)))
			.hasStatusOk()
			.bodyJson()
			.satisfies((json) -> {
				assertThat(json).extractingPath("$.items.length()").isEqualTo(5);
				assertThat(json).extractingPath("$.items[0].playlist.name").isNotNull();
				assertThat(json).extractingPath("$.items[0].track.is_liked").isNotNull();
			});
	}

	@Test
	void deletingAPlaylistIsForItsOwner() {
		TestUser owner = newUser("deleter");
		TestUser other = newUser("bystander");
		int id = createPublicPlaylist(owner.token(), "Doomed", SOMETHING);
		assertThat(this.mvc.delete().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, other.bearer()))
			.hasStatus(403);
		assertThat(this.mvc.delete().uri(API + "/playlists/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
			.hasStatus(204);
		assertThat(this.mvc.get().uri(API + "/playlists/{id}", id)).hasStatus(404);
	}

	private MvcTestResult addTracks(TestUser user, int id, String ifMatch, String trackIds, Integer position) {
		var request = this.mvc.post()
			.uri(API + "/playlists/{id}/tracks", id)
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content((position != null) ? """
					{"track_ids": %s, "position": %d}""".formatted(trackIds, position) : """
					{"track_ids": %s}""".formatted(trackIds));
		if (ifMatch != null) {
			request.header(HttpHeaders.IF_MATCH, ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult move(TestUser user, int id, int from, int to) {
		return this.mvc.post()
			.uri(API + "/playlists/{id}/tracks/move", id)
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.header(HttpHeaders.IF_MATCH, etag(user, id))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"from": %d, "to": %d}""".formatted(from, to))
			.exchange();
	}

	private MvcTestResult patch(TestUser user, int id, String ifMatch, String body) {
		var request = this.mvc.patch()
			.uri(API + "/playlists/{id}", id)
			.header(HttpHeaders.AUTHORIZATION, user.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (ifMatch != null) {
			request.header(HttpHeaders.IF_MATCH, ifMatch);
		}
		return request.exchange();
	}

	private MvcTestResult putCollaborator(TestUser owner, int id, String username, String role) {
		return this.mvc.put()
			.uri(API + "/playlists/{id}/collaborators/{username}", id, username)
			.header(HttpHeaders.AUTHORIZATION, owner.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "%s"}""".formatted(role))
			.exchange();
	}

	private String etag(TestUser user, int id) {
		return tracks(user.token(), id).getResponse().getHeader(HttpHeaders.ETAG);
	}

	private MvcTestResult tracks(String token, int id) {
		MvcTestResult result = this.mvc.get()
			.uri(API + "/playlists/{id}/tracks?limit=100", id)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.exchange();
		assertThat(result).hasStatusOk();
		return result;
	}

	private List<Integer> entries(String token, int id) {
		return read(tracks(token, id), "$.items[*].track.track_id");
	}

}
