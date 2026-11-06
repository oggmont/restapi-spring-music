package com.oggmont.music.catalog;

import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Genres (a tree through {@code parent_genre_id}) and labels.
 */
@Repository
class GenreRepository {

	private final JdbcClient jdbc;

	GenreRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The genre tree, alphabetical at every level. */
	List<GenreNode> tree() {
		record Row(int genreId, String name, @Nullable Integer parentId) {
		}
		List<Row> rows = this.jdbc.sql("SELECT genre_id, name, parent_genre_id FROM genre ORDER BY name")
			.query((rs, rowNum) -> new Row(rs.getInt("genre_id"), rs.getString("name"),
					Rows.integer(rs, "parent_genre_id")))
			.list();
		Map<Integer, List<Row>> children = new LinkedHashMap<>();
		rows.forEach((row) -> children.computeIfAbsent(row.parentId(), (key) -> new ArrayList<>()).add(row));
		class Builder {

			GenreNode build(Row row, int depth) {
				List<GenreNode> kids = (depth > 32) ? List.of()
						: children.getOrDefault(row.genreId(), List.of())
							.stream()
							.map((child) -> build(child, depth + 1))
							.toList();
				return new GenreNode(row.genreId(), row.name(), kids);
			}

		}
		Builder builder = new Builder();
		Set<Integer> ids = rows.stream().map(Row::genreId).collect(Collectors.toSet());
		return rows.stream()
			.filter((row) -> row.parentId() == null || !ids.contains(row.parentId()))
			.map((row) -> builder.build(row, 0))
			.toList();
	}

	boolean exists(int genreId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM genre WHERE genre_id = :genreId)")
			.param("genreId", genreId)
			.query(Boolean.class)
			.single();
	}

	/**
	 * Albums in a genre or any of its subgenres (example 10 in examples.sql); with
	 * {@code primaryOnly}, only albums whose primary genre is in the tree.
	 */
	Page<AlbumCard> albums(int genreId, boolean primaryOnly, PageParams page) {
		String matching = """
				WITH RECURSIVE tree AS (
				    SELECT genre_id FROM genre WHERE genre_id = :genreId
				    UNION
				    SELECT g.genre_id FROM genre g JOIN tree t ON g.parent_genre_id = t.genre_id
				), matching AS (
				    SELECT DISTINCT ag.album_id FROM album_genre ag
				    WHERE ag.genre_id IN (SELECT genre_id FROM tree)%s
				)
				""".formatted(primaryOnly ? " AND ag.is_primary" : "");
		List<AlbumCard> items = this.jdbc.sql(matching + """
				, page AS (
				    SELECT al.album_id, al.release_year, al.title
				    FROM album al
				    JOIN matching m ON m.album_id = al.album_id
				    ORDER BY al.release_year NULLS LAST, lower(al.title), al.album_id
				    LIMIT :limit OFFSET :offset
				)
				SELECT
				""" + CatalogSql.ALBUM_CARD_COLUMNS + """

				FROM page
				JOIN album_summary als ON als.album_id = page.album_id
				ORDER BY page.release_year NULLS LAST, lower(page.title), page.album_id
				""")
			.param("genreId", genreId)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> CatalogSql.albumCard(rs))
			.list();
		long total = this.jdbc.sql(matching + "SELECT count(*) FROM matching")
			.param("genreId", genreId)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	/** A label with a page of its albums, oldest first. */
	Optional<LabelDetail> label(String slug, PageParams page) {
		record LabelRow(int labelId, String name, String slug, @Nullable String countryCode) {
		}
		return this.jdbc.sql("SELECT label_id, name, slug, country_code FROM label WHERE slug = :slug")
			.param("slug", slug)
			.query((rs, rowNum) -> new LabelRow(rs.getInt("label_id"), rs.getString("name"), rs.getString("slug"),
					rs.getString("country_code")))
			.optional()
			.map((label) -> {
				List<AlbumCard> albums = this.jdbc.sql("""
						WITH page AS (
						    SELECT album_id, release_date, release_year, title FROM album
						    WHERE label_id = :labelId
						    ORDER BY release_date NULLS LAST, release_year NULLS LAST, lower(title), album_id
						    LIMIT :limit OFFSET :offset
						)
						SELECT
						""" + CatalogSql.ALBUM_CARD_COLUMNS + """

						FROM page
						JOIN album_summary als ON als.album_id = page.album_id
						ORDER BY page.release_date NULLS LAST, page.release_year NULLS LAST, lower(page.title), page.album_id
						""")
					.param("labelId", label.labelId())
					.param("limit", page.resolvedLimit())
					.param("offset", page.resolvedOffset())
					.query((rs, rowNum) -> CatalogSql.albumCard(rs))
					.list();
				long total = this.jdbc.sql("SELECT count(*) FROM album WHERE label_id = :labelId")
					.param("labelId", label.labelId())
					.query(Long.class)
					.single();
				return new LabelDetail(label.labelId(), label.name(), label.slug(), label.countryCode(),
						Page.of(albums, page, total));
			});
	}

	/**
	 * @param genreId {@code genre.genre_id}
	 * @param name the genre's name
	 * @param children its subgenres
	 */
	record GenreNode(int genreId, String name, List<GenreNode> children) {
	}

	/**
	 * @param labelId {@code label.label_id}
	 * @param name the label's name
	 * @param slug URL key
	 * @param countryCode ISO 3166-1 alpha-2
	 * @param albums a page of its albums
	 */
	record LabelDetail(int labelId, String name, String slug, @Nullable String countryCode, Page<AlbumCard> albums) {
	}

}
