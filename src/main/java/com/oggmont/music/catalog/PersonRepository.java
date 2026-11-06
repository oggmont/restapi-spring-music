package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.common.jdbc.Rows;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * People: band members and songwriters (examples 5 and 8 in examples.sql).
 */
@Repository
class PersonRepository {

	private final JdbcClient jdbc;

	PersonRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<PersonDetail> find(int personId) {
		Optional<PersonDetail> person = this.jdbc.sql("""
				SELECT p.person_id, p.name, p.birth_name, p.birth_date, p.death_date, p.birth_place, p.image_url,
				       a.artist_id, a.name AS artist_name, a.slug AS artist_slug
				FROM person p
				LEFT JOIN artist a ON a.person_id = p.person_id
				WHERE p.person_id = :personId
				""")
			.param("personId", personId)
			.query((rs, rowNum) -> {
				Integer soloArtistId = Rows.integer(rs, "artist_id");
				return new PersonDetail(rs.getInt("person_id"), rs.getString("name"), rs.getString("birth_name"),
						Rows.date(rs, "birth_date"), Rows.date(rs, "death_date"), rs.getString("birth_place"),
						rs.getString("image_url"),
						(soloArtistId != null)
								? new ArtistRef(soloArtistId, rs.getString("artist_name"), rs.getString("artist_slug"))
								: null,
						List.of(), List.of());
			})
			.optional();
		return person.map((p) -> p.with(bands(personId), credits(personId)));
	}

	private List<Band> bands(int personId) {
		return this.jdbc.sql("""
				SELECT a.artist_id, a.name, a.slug, m.roles, m.joined_year, m.left_year
				FROM artist_member m
				JOIN artist a ON a.artist_id = m.artist_id
				WHERE m.person_id = :personId
				ORDER BY m.joined_year NULLS LAST, a.name
				""")
			.param("personId", personId)
			.query((rs, rowNum) -> new Band(
					new ArtistRef(rs.getInt("artist_id"), rs.getString("name"), rs.getString("slug")),
					Rows.strings(rs, "roles"), Rows.integer(rs, "joined_year"), Rows.integer(rs, "left_year")))
			.list();
	}

	private List<SongCredit> credits(int personId) {
		return this.jdbc.sql("""
				SELECT s.song_id, s.title, c.role, c.credited_as,
				       ar.artist_id, ar.name AS artist_name, ar.slug AS artist_slug
				FROM credit c
				JOIN song s ON s.song_id = c.song_id
				LEFT JOIN artist ar ON ar.artist_id = s.artist_id
				WHERE c.person_id = :personId
				ORDER BY s.title, s.song_id, c.role
				""")
			.param("personId", personId)
			.query((rs, rowNum) -> {
				Integer artistId = Rows.integer(rs, "artist_id");
				return new SongCredit(rs.getInt("song_id"), rs.getString("title"), rs.getString("role"),
						rs.getString("credited_as"),
						(artistId != null)
								? new ArtistRef(artistId, rs.getString("artist_name"), rs.getString("artist_slug")) : null);
			})
			.list();
	}

	/**
	 * A person page.
	 *
	 * @param personId {@code person.person_id}
	 * @param name the name they are known by
	 * @param birthName name at birth
	 * @param birthDate date of birth
	 * @param deathDate date of death
	 * @param birthPlace place of birth
	 * @param imageUrl picture
	 * @param soloArtist the solo artist they are, if any
	 * @param bands the bands they were in, one entry per stint
	 * @param credits their songwriting credits
	 */
	record PersonDetail(int personId, String name, @Nullable String birthName, @Nullable LocalDate birthDate,
			@Nullable LocalDate deathDate, @Nullable String birthPlace, @Nullable String imageUrl,
			@Nullable ArtistRef soloArtist, List<Band> bands, List<SongCredit> credits) {

		PersonDetail with(List<Band> bands, List<SongCredit> credits) {
			return new PersonDetail(this.personId, this.name, this.birthName, this.birthDate, this.deathDate,
					this.birthPlace, this.imageUrl, this.soloArtist, bands, credits);
		}

	}

	record Band(ArtistRef artist, List<String> roles, @Nullable Integer joinedYear, @Nullable Integer leftYear) {
	}

	record SongCredit(int songId, String title, String role,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String creditedAs, @Nullable ArtistRef artist) {
	}

}
