package com.oggmont.music.reference;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oggmont.music.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Allowed values for dropdowns and validation, straight from the lookup tables. They
 * rarely change, so responses carry a long {@code Cache-Control}.
 */
@RestController
@Tag(name = "Reference data", description = "Allowed values: countries, types, roles, visibilities")
class ReferenceController {

	private final JdbcClient jdbc;

	ReferenceController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/reference/{name}")
	@Operation(summary = "countries, artist-types, album-types, artist-roles, credit-roles, playlist-visibilities or playlist-roles")
	ResponseEntity<List<ReferenceValue>> values(@PathVariable String name) {
		Table table = Table.byName(name);
		List<ReferenceValue> values = this.jdbc.sql(table.sql)
			.query((rs, rowNum) -> new ReferenceValue(rs.getString("code"), rs.getString("description"),
					table.hasSongwriting ? rs.getBoolean("is_songwriting") : null,
					table.hasCanEdit ? rs.getBoolean("can_edit") : null))
			.list();
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic()).body(values);
	}

	/** The lookup tables that can be listed; table names never come from the request. */
	enum Table {

		COUNTRIES("countries", "SELECT country_code AS code, name AS description FROM country ORDER BY name"),
		ARTIST_TYPES("artist-types", "SELECT code, description FROM artist_type ORDER BY code"),
		ALBUM_TYPES("album-types", "SELECT code, description FROM album_type ORDER BY code"),
		ARTIST_ROLES("artist-roles", "SELECT code, description FROM artist_role ORDER BY code"),
		CREDIT_ROLES("credit-roles", "SELECT code, description, is_songwriting FROM credit_role ORDER BY code"),
		PLAYLIST_VISIBILITIES("playlist-visibilities",
				"SELECT code, description FROM playlist_visibility ORDER BY code"),
		PLAYLIST_ROLES("playlist-roles", "SELECT code, description, can_edit FROM playlist_role ORDER BY code");

		private final String path;

		private final String sql;

		private final boolean hasSongwriting;

		private final boolean hasCanEdit;

		Table(String path, String sql) {
			this.path = path;
			this.sql = sql;
			this.hasSongwriting = sql.contains("is_songwriting");
			this.hasCanEdit = sql.contains("can_edit");
		}

		static Table byName(String name) {
			return Arrays.stream(values())
				.filter((table) -> table.path.equals(name))
				.findFirst()
				.orElseThrow(() -> ApiException.notFound("No reference data named '" + name + "'. Available: "
						+ Arrays.stream(values()).map((table) -> table.path).collect(Collectors.joining(", ")) + "."));
		}

	}

	/**
	 * @param code the value to send
	 * @param description what it means (the country's name for countries)
	 * @param isSongwriting credit roles: whether it shows in a track's composer text
	 * @param canEdit playlist roles: whether it can change the track list
	 */
	record ReferenceValue(String code, String description,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean isSongwriting,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Boolean canEdit) {
	}

}
