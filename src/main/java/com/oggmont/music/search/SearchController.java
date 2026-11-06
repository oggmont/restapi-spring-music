package com.oggmont.music.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One search box across everything, wrapping the schema's {@code search_catalog(q, limit)}:
 * accent-insensitive substring and full-text matches across artists, albums, songs,
 * tracks, people, public playlists and users, best first.
 */
@RestController
@Tag(name = "Search", description = "Search the catalog, public playlists and users")
class SearchController {

	static final Set<String> KINDS = Set.of("artist", "album", "song", "track", "person", "playlist", "user");

	/** {@code search_catalog} limits before {@code types} filters, so filtering asks it for more rows. */
	private static final int FILTERED_FETCH = 1000;

	private final JdbcClient jdbc;

	SearchController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/search")
	@Operation(summary = "Search: ?q=come together&types=song,track&limit=5")
	SearchResults search(@RequestParam @NotBlank @Size(max = 100) String q,
			@RequestParam(required = false) @Nullable String types,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		String query = q.trim();
		List<String> kinds = kinds(types);
		String sql = kinds.isEmpty() ? "SELECT kind, id, name, slug, context, score FROM search_catalog(:q, :limit)"
				: """
						SELECT kind, id, name, slug, context, score
						FROM search_catalog(:q, :fetch)
						WHERE kind = ANY (CAST(:kinds AS text[]))
						LIMIT :limit
						""";
		JdbcClient.StatementSpec statement = this.jdbc.sql(sql).param("q", query).param("limit", limit);
		if (!kinds.isEmpty()) {
			statement.param("fetch", FILTERED_FETCH).param("kinds", kinds.toArray(String[]::new));
		}
		List<SearchHit> hits = statement
			.query((rs, rowNum) -> new SearchHit(rs.getString("kind"), rs.getInt("id"), rs.getString("name"),
					rs.getString("slug"), rs.getString("context"), rs.getDouble("score")))
			.list();
		return new SearchResults(query, hits);
	}

	private static List<String> kinds(@Nullable String types) {
		if (types == null || types.isBlank()) {
			return List.of();
		}
		Set<String> kinds = new LinkedHashSet<>();
		for (String type : types.split(",")) {
			String kind = type.trim().toLowerCase(Locale.ROOT);
			if (kind.isEmpty()) {
				continue;
			}
			if (!KINDS.contains(kind)) {
				throw ApiException.unprocessable("invalid_type",
						"Unknown type '" + kind + "'. Use any of: " + String.join(", ", Arrays.asList(
								"artist", "album", "song", "track", "person", "playlist", "user")) + ".", "types");
			}
			kinds.add(kind);
		}
		return List.copyOf(kinds);
	}

	/**
	 * @param query the search terms, as searched
	 * @param items matches, best first
	 */
	record SearchResults(String query, List<SearchHit> items) {
	}

	/**
	 * @param kind artist, album, song, track, person, playlist or user
	 * @param id the ID of the match
	 * @param name its name or title
	 * @param slug URL key, where the kind has one (artists, albums; the username for users)
	 * @param context what it belongs to, e.g. the artist of a song
	 * @param score relevance (higher is better)
	 */
	record SearchHit(String kind, int id, String name, @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String slug,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String context, double score) {
	}

}
