package com.oggmont.music.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Artists: {@code artist}, the {@code artist_summary} view and {@code artist_member}.
 */
@Repository
public class ArtistRepository {

	private final JdbcClient jdbc;

	ArtistRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Artists, filtered and sorted; the page is picked from {@code artist} before the view is joined. */
	Page<ArtistCard> list(Filter filter, PageParams page) {
		List<String> conditions = new ArrayList<>();
		if (filter.type() != null) {
			conditions.add("a.artist_type = :type");
		}
		if (filter.country() != null) {
			conditions.add("a.country_code = :country");
		}
		String with = "";
		if (filter.genre() != null) {
			// The genre and its subgenres, matched by slug ("rock", "progressive-rock").
			with = """
					WITH RECURSIVE genre_tree AS (
					    SELECT genre_id FROM genre WHERE slugify(name) = slugify(:genre)
					    UNION
					    SELECT g.genre_id FROM genre g JOIN genre_tree t ON g.parent_genre_id = t.genre_id
					)
					""";
			conditions.add("""
					EXISTS (SELECT 1 FROM album al
					        JOIN album_genre ag ON ag.album_id = al.album_id
					        WHERE ag.genre_id IN (SELECT genre_id FROM genre_tree)
					          AND (al.artist_id = a.artist_id
					               OR EXISTS (SELECT 1 FROM album_artist aa
					                          WHERE aa.album_id = al.album_id AND aa.artist_id = a.artist_id)))""");
		}
		String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
		String innerOrder = filter.sort().orderBy("a");
		String sql = with + (with.isEmpty() ? "WITH " : ", ") + """
				page AS (
				    SELECT a.artist_id, a.name, a.follower_count FROM artist a
				    %s
				    ORDER BY %s
				    LIMIT :limit OFFSET :offset
				)
				SELECT %s
				FROM page
				JOIN artist_summary ars ON ars.artist_id = page.artist_id
				ORDER BY %s
				""".formatted(where, innerOrder, CatalogSql.ARTIST_CARD_COLUMNS, filter.sort().orderBy("page"));
		List<ArtistCard> items = bind(this.jdbc.sql(sql), filter).param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> CatalogSql.artistCard(rs))
			.list();
		long total = bind(this.jdbc.sql(with + "SELECT count(*) FROM artist a " + where), filter).query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	public Optional<ArtistDetail> find(String slug, @Nullable Integer me) {
		return this.jdbc.sql("""
				SELECT a.artist_id, a.name, a.slug, a.artist_type, a.person_id, a.country_code, a.city,
				       a.formed_year, a.disbanded_year, a.biography, a.image_url, a.musicbrainz_id, a.follower_count,
				       ars.album_count, ars.track_count,
				       CASE WHEN CAST(:me AS integer) IS NULL THEN NULL
				            ELSE EXISTS (SELECT 1 FROM artist_follow f WHERE f.user_id = :me AND f.artist_id = a.artist_id)
				       END AS is_followed_by_me
				FROM artist a
				JOIN artist_summary ars ON ars.artist_id = a.artist_id
				WHERE a.slug = :slug
				""")
			.param("me", me, Types.INTEGER)
			.param("slug", slug)
			.query((rs, rowNum) -> new ArtistDetail(rs.getInt("artist_id"), rs.getString("name"), rs.getString("slug"),
					rs.getString("artist_type"), Rows.integer(rs, "person_id"), rs.getString("country_code"),
					rs.getString("city"), Rows.integer(rs, "formed_year"), Rows.integer(rs, "disbanded_year"),
					rs.getString("biography"), rs.getString("image_url"), Rows.uuid(rs, "musicbrainz_id"),
					rs.getInt("follower_count"), rs.getLong("album_count"), rs.getLong("track_count"),
					Rows.bool(rs, "is_followed_by_me")))
			.optional();
	}

	Optional<Integer> idBySlug(String slug) {
		return this.jdbc.sql("SELECT artist_id FROM artist WHERE slug = :slug")
			.param("slug", slug)
			.query(Integer.class)
			.optional();
	}

	/**
	 * Every album the artist is on, including collaborations ({@code album_artist}), with
	 * the artist's role, oldest first.
	 */
	Page<AlbumCard> albums(int artistId, @Nullable String albumType, PageParams page) {
		String credited = """
				WITH credited AS (
				    SELECT aa.album_id, aa.role FROM album_artist aa WHERE aa.artist_id = :artistId
				    UNION ALL
				    SELECT al.album_id, 'main' FROM album al
				    WHERE al.artist_id = :artistId
				      AND NOT EXISTS (SELECT 1 FROM album_artist aa
				                      WHERE aa.album_id = al.album_id AND aa.artist_id = :artistId)
				)
				""";
		String filter = (albumType != null) ? "WHERE al.album_type = :albumType" : "";
		List<AlbumCard> items = typed(this.jdbc.sql(credited + """
				, page AS (
				    SELECT c.album_id, c.role, al.release_date, al.release_year, al.title
				    FROM credited c
				    JOIN album al ON al.album_id = c.album_id
				    %s
				    ORDER BY al.release_date NULLS LAST, al.release_year NULLS LAST, lower(al.title), al.album_id
				    LIMIT :limit OFFSET :offset
				)
				SELECT %s, page.role
				FROM page
				JOIN album_summary als ON als.album_id = page.album_id
				ORDER BY page.release_date NULLS LAST, page.release_year NULLS LAST, lower(page.title), page.album_id
				""".formatted(filter, CatalogSql.ALBUM_CARD_COLUMNS)), albumType).param("artistId", artistId)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> CatalogSql.albumCard(rs).withRole(rs.getString("role")))
			.list();
		long total = typed(this.jdbc.sql(credited
				+ "SELECT count(*) FROM credited c JOIN album al ON al.album_id = c.album_id " + filter), albumType)
			.param("artistId", artistId)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	/** The band line-up, one row per stint; a solo artist answers with its person. */
	List<Member> members(int artistId) {
		List<Member> members = this.jdbc.sql("""
				SELECT p.person_id, p.name, m.roles, m.joined_year, m.left_year
				FROM artist_member m
				JOIN person p ON p.person_id = m.person_id
				WHERE m.artist_id = :artistId
				ORDER BY m.joined_year, m.left_year DESC, p.name, m.artist_member_id
				""")
			.param("artistId", artistId)
			.query((rs, rowNum) -> new Member(rs.getInt("person_id"), rs.getString("name"), Rows.strings(rs, "roles"),
					Rows.integer(rs, "joined_year"), Rows.integer(rs, "left_year")))
			.list();
		if (!members.isEmpty()) {
			return members;
		}
		return this.jdbc.sql("""
				SELECT p.person_id, p.name, a.formed_year, a.disbanded_year
				FROM artist a
				JOIN person p ON p.person_id = a.person_id
				WHERE a.artist_id = :artistId
				""")
			.param("artistId", artistId)
			.query((rs, rowNum) -> new Member(rs.getInt("person_id"), rs.getString("name"), List.of(),
					Rows.integer(rs, "formed_year"), Rows.integer(rs, "disbanded_year")))
			.list();
	}

	private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Filter filter) {
		if (filter.type() != null) {
			statement.param("type", filter.type());
		}
		if (filter.country() != null) {
			statement.param("country", filter.country());
		}
		if (filter.genre() != null) {
			statement.param("genre", filter.genre());
		}
		return statement;
	}

	private static JdbcClient.StatementSpec typed(JdbcClient.StatementSpec statement, @Nullable String albumType) {
		return (albumType != null) ? statement.param("albumType", albumType) : statement;
	}

	/** Sort orders of {@code GET /artists}. */
	enum Sort {

		NAME, FOLLOWERS;

		String orderBy(String alias) {
			return switch (this) {
				case NAME -> "lower(%1$s.name), %1$s.artist_id".formatted(alias);
				case FOLLOWERS -> "%1$s.follower_count DESC, lower(%1$s.name), %1$s.artist_id".formatted(alias);
			};
		}

	}

	/**
	 * @param type artist type code, e.g. {@code group}
	 * @param country ISO country code
	 * @param genre genre name or slug, including its subgenres
	 * @param sort sort order
	 */
	record Filter(@Nullable String type, @Nullable String country, @Nullable String genre, Sort sort) {
	}

	/**
	 * An artist page ({@code GET /artists/{slug}}).
	 *
	 * @param artistId {@code artist.artist_id}
	 * @param name the artist's name
	 * @param slug URL key
	 * @param artistType solo, group, orchestra...
	 * @param personId for solo artists, the person behind the name ({@code /people/{id}})
	 * @param countryCode ISO 3166-1 alpha-2
	 * @param city home town
	 * @param formedYear year formed (or debut)
	 * @param disbandedYear year disbanded
	 * @param biography short biography
	 * @param imageUrl picture
	 * @param musicbrainzId MusicBrainz artist ID
	 * @param followerCount number of followers
	 * @param albumCount albums credited to them or listing them
	 * @param trackCount tracks on those albums, plus track appearances
	 * @param isFollowedByMe whether the caller follows them; only sent with a token
	 */
	public record ArtistDetail(int artistId, String name, String slug, @Nullable String artistType, @Nullable Integer personId,
			@Nullable String countryCode, @Nullable String city, @Nullable Integer formedYear,
			@Nullable Integer disbandedYear, @Nullable String biography, @Nullable String imageUrl,
			@Nullable UUID musicbrainzId, int followerCount, long albumCount, long trackCount,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean isFollowedByMe) {
	}

	/**
	 * One stint of a person in a band.
	 *
	 * @param personId {@code person.person_id}
	 * @param name the person's name
	 * @param roles instruments and roles
	 * @param joinedYear year joined
	 * @param leftYear year left
	 */
	record Member(int personId, String name, List<String> roles, @Nullable Integer joinedYear,
			@Nullable Integer leftYear) {
	}

}
