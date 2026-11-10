package com.oggmont.music.admin;

import com.oggmont.music.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog and account administration, and the roles that guard them.
 */
class AdminIT extends IntegrationTest {

	@Test
	void rolesGuardTheAdminEndpoints() {
		TestUser listener = newUser("listener");
		assertThat(post(listener.token(), "/admin/artists", """
				{"name": "Nope"}""")).hasStatus(403).bodyJson().extractingPath("$.error.code").isEqualTo("forbidden");
		assertThat(this.mvc.post().uri(API + "/admin/artists").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.hasStatus(401);

		TestUser curator = newUser("curator");
		assertThat(this.mvc.put()
			.uri(API + "/admin/users/{u}/role", curator.username())
			.header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "curator"}""")).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("curator");
		// Roles are read on every request, so the existing token works at once.
		assertThat(post(curator.token(), "/admin/artists", """
				{"name": "Curated Artist %d"}""".formatted(System.nanoTime() % 100000))).hasStatus(201);
		assertThat(this.mvc.put()
			.uri(API + "/admin/users/{u}/role", listener.username())
			.header(HttpHeaders.AUTHORIZATION, curator.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "admin"}""")).as("account administration is for admins").hasStatus(403);
	}

	@Test
	void adminsManageRolesAndPasswordsButNotTheirOwnRole() {
		String admin = adminToken();
		assertThat(this.mvc.put()
			.uri(API + "/admin/users/{u}/role", ADMIN_USERNAME)
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "listener"}""")).hasStatus(422);
		assertThat(this.mvc.put()
			.uri(API + "/admin/users/emma.schulz/role")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"role": "king"}""")).hasStatus(422);
		// A seed user gets a password and can log in.
		String emma = seedUser("emma.schulz");
		assertThat(this.mvc.get().uri(API + "/me").header(HttpHeaders.AUTHORIZATION, bearer(emma))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.username")
			.isEqualTo("emma.schulz");
		assertThat(this.mvc.put()
			.uri(API + "/admin/users/nobody.here/password")
			.header(HttpHeaders.AUTHORIZATION, bearer(admin))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"password": "long-enough-password"}""")).hasStatus(404);
	}

	@Test
	void artistsGetGeneratedSlugsThatDontFollowRenames() {
		String admin = adminToken();
		String name = "Los Ejemplos " + Long.toString(System.nanoTime() % 1_000_000, 36);
		MvcTestResult created = post(admin, "/admin/artists", """
				{"name": "%s", "artist_type": "group", "country_code": "mx", "formed_year": 1989}""".formatted(name))
			.exchange();
		assertThat(created).hasStatus(201);
		int id = read(created, "$.artist_id");
		String slug = read(created, "$.slug");
		assertThat(slug).startsWith("los-ejemplos-");
		assertThat(read(created, "$.country_code").toString()).isEqualTo("MX");

		assertThat(post(admin, "/admin/artists", """
				{"name": "Copycat", "slug": "%s"}""".formatted(slug))).hasStatus(409)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("slug_taken");
		assertThat(patch(admin, "/admin/artists/" + id, """
				{"name": "Renamed Band"}""")).hasStatusOk().bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.name").isEqualTo("Renamed Band");
			assertThat(json).extractingPath("$.slug").isEqualTo(slug);
			assertThat(json).extractingPath("$.formed_year").isEqualTo(1989);
		});
		assertThat(patch(admin, "/admin/artists/" + id, """
				{"disbanded_year": 1980}""")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("invalid_years");
		assertThat(patch(admin, "/admin/artists/999999", """
				{"city": "Nowhere"}""")).hasStatus(404);
	}

	@Test
	void albumsAreCreatedWithTheirArtistsAndGenresInOneTransaction() {
		String admin = adminToken();
		int artistId = read(post(admin, "/admin/artists", """
				{"name": "Album Maker %d", "artist_type": "group"}""".formatted(System.nanoTime() % 100000)).exchange(),
				"$.artist_id");

		// The database checks at commit that the album lists its credited artist.
		assertThat(post(admin, "/admin/albums", """
				{"title": "Orphan", "artist_id": %d, "artists": [{"artist_id": 276}]}""".formatted(artistId)))
			.hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("integrity_violation");
		assertThat(post(admin, "/admin/albums", """
				{"title": "Two primaries", "artist_id": %d, "artists": [{"artist_id": %d}],
				 "genres": [{"genre_id": 1, "is_primary": true}, {"genre_id": 7, "is_primary": true}]}"""
			.formatted(artistId, artistId))).hasStatus(422);

		MvcTestResult album = post(admin, "/admin/albums", """
				{"title": "First Album", "artist_id": %d, "album_type": "studio", "release_date": "1994-08-02",
				 "artists": [{"artist_id": %d}],
				 "genres": [{"genre_id": 1, "is_primary": false}, {"genre_id": 7, "is_primary": true}]}"""
			.formatted(artistId, artistId)).exchange();
		assertThat(album).hasStatus(201).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.slug").isEqualTo("first-album");
			assertThat(json).extractingPath("$.release_year").isEqualTo(1994);
			assertThat(json).extractingPath("$.primary_genre").isEqualTo("Latin");
		});
		int albumId = read(album, "$.album_id");

		MvcTestResult tracks = post(admin, "/admin/albums/" + albumId + "/tracks", """
				{"tracks": [{"name": "Uno", "track_number": 1, "milliseconds": 200000},
				            {"name": "Dos", "track_number": 2, "milliseconds": 185500, "genre_id": 4}]}""").exchange();
		assertThat(tracks).hasStatus(201).bodyJson().satisfies((json) -> {
			assertThat(json).extractingPath("$.total").isEqualTo(2);
			assertThat(json).extractingPath("$.items[1].duration").isEqualTo("3:05");
		});
		assertThat(post(admin, "/admin/albums/" + albumId + "/tracks", """
				{"tracks": [{"name": "Again", "track_number": 1, "milliseconds": 1000}]}""")).hasStatus(409);
		// New tracks are in song search right away (its view is refreshed after the write).
		List<String> found = read(this.mvc.get().uri(API + "/tracks?q=album maker&limit=100").exchange(),
				"$.items[*].album.title");
		assertThat(found).contains("First Album");

		// Track 2's own genre was added to the album; it can't be removed while in use.
		assertThat(put(admin, "/admin/albums/" + albumId + "/genres", """
				{"genres": [{"genre_id": 1, "is_primary": true}]}""")).hasStatus(422)
			.bodyJson()
			.extractingPath("$.error.code")
			.isEqualTo("genre_in_use");
		assertThat(put(admin, "/admin/albums/" + albumId + "/genres", """
				{"genres": [{"genre_id": 1, "is_primary": true}, {"genre_id": 4, "is_primary": false}]}"""))
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.genres[0].name")
			.isEqualTo("Rock");
		assertThat(put(admin, "/admin/albums/999999/genres", """
				{"genres": []}""")).hasStatus(404);
	}

	@Test
	void creditsRegenerateTheComposerTextOfEveryRecording() {
		String admin = adminToken();
		// People and songs have no admin endpoints (the design loads them with SQL, like the seed).
		int personId = this.jdbc.sql("INSERT INTO person (name) VALUES ('Credit Tester') RETURNING person_id")
			.query(Integer.class)
			.single();
		int songId = this.jdbc.sql("INSERT INTO song (title) VALUES ('Credited Song') RETURNING song_id")
			.query(Integer.class)
			.single();
		int artistId = read(post(admin, "/admin/artists", """
				{"name": "Credit Band %d"}""".formatted(System.nanoTime() % 100000)).exchange(), "$.artist_id");
		int albumId = read(post(admin, "/admin/albums", """
				{"title": "Credits", "artist_id": %d, "artists": [{"artist_id": %d}]}""".formatted(artistId, artistId))
			.exchange(), "$.album_id");
		assertThat(post(admin, "/admin/albums/" + albumId + "/tracks", """
				{"tracks": [{"name": "Credited Song", "track_number": 1, "milliseconds": 1000, "song_id": %d}]}"""
			.formatted(songId))).hasStatus(201);

		assertThat(post(admin, "/admin/songs/" + songId + "/credits", """
				{"credits": [{"person_id": %d, "role": "writer", "credited_as": "C. Tester"}]}""".formatted(personId)))
			.hasStatus(201)
			.bodyJson()
			.extractingPath("$.credits[0].credited_as")
			.isEqualTo("C. Tester");
		String composer = this.jdbc.sql("SELECT composer FROM track WHERE song_id = :songId")
			.param("songId", songId)
			.query(String.class)
			.single();
		assertThat(composer).isEqualTo("C. Tester");
		assertThat(post(admin, "/admin/songs/" + songId + "/credits", """
				{"credits": [{"person_id": %d, "role": "writer"}]}""".formatted(personId))).hasStatus(409);
		assertThat(post(admin, "/admin/songs/999999/credits", """
				{"credits": [{"person_id": %d, "role": "writer"}]}""".formatted(personId))).hasStatus(404);
	}

	private org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder post(String token,
			String path, String body) {
		return this.mvc.post()
			.uri(API + path)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
	}

	private MvcTestResult patch(String token, String path, String body) {
		return this.mvc.patch()
			.uri(API + path)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult put(String token, String path, String body) {
		return this.mvc.put()
			.uri(API + path)
			.header(HttpHeaders.AUTHORIZATION, bearer(token))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

}
