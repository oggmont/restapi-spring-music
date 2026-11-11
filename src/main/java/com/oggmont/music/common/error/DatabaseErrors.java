package com.oggmont.music.common.error;

import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.http.HttpStatus;

/**
 * Translates PostgreSQL errors into API errors.
 * <p>
 * The database enforces most rules itself (constraints, triggers), so instead of
 * repeating the checks the API maps the SQLSTATE, and the constraint name tells it which
 * field to blame. See "Mapping database errors to HTTP" in API-Design.md:
 * <table>
 * <tr><th>SQLSTATE</th><th>Name</th><th>HTTP</th></tr>
 * <tr><td>23505</td><td>unique_violation</td><td>409</td></tr>
 * <tr><td>23503</td><td>foreign_key_violation</td><td>422 (404 when the value came from the URL; services handle that)</td></tr>
 * <tr><td>23514</td><td>check_violation</td><td>422</td></tr>
 * <tr><td>23P01</td><td>exclusion_violation</td><td>422</td></tr>
 * <tr><td>23000</td><td>integrity_constraint_violation (raised by triggers)</td><td>422</td></tr>
 * <tr><td>42501</td><td>insufficient_privilege (raised by triggers)</td><td>403</td></tr>
 * </table>
 */
public final class DatabaseErrors {

	public static final String UNIQUE_VIOLATION = "23505";

	public static final String FOREIGN_KEY_VIOLATION = "23503";

	public static final String CHECK_VIOLATION = "23514";

	public static final String EXCLUSION_VIOLATION = "23P01";

	public static final String INTEGRITY_CONSTRAINT_VIOLATION = "23000";

	public static final String NOT_NULL_VIOLATION = "23502";

	public static final String INSUFFICIENT_PRIVILEGE = "42501";

	/** Why {@code visibility} can't be sent or changed. */
	public static final String VISIBILITY_MESSAGE = "A playlist's visibility follows its favorites: it is public while "
			+ "anyone has it as a favorite, and private otherwise. Favorite it to make it public.";

	/** Constraint name -> what to tell the client. */
	private static final Map<String, Known> KNOWN = Map.ofEntries(
			// Users
			conflict("app_user_username_key", "username_taken", "username", "That username is already taken."),
			conflict("app_user_email_key", "email_taken", "email", "An account with that email already exists."),
			invalid("app_user_username_check", "invalid_username", "username",
					"Usernames are 3 to 30 characters: lowercase letters, digits, '_' and '.'."),
			invalid("app_user_email_check", "invalid_email", "email", "That isn't a valid email address."),
			invalid("app_user_country_code_fkey", "unknown_country", "country_code",
					"Unknown country code (use ISO 3166-1 alpha-2, e.g. MX)."),
			invalid("user_follow_self_check", "cannot_follow_self", null, "You can't follow yourself."),
			invalid("user_account_role_fkey", "unknown_role", "role", "Role must be listener, curator or admin."),
			// Playlists
			invalid("playlist_visibility_fkey", "unknown_visibility", "visibility",
					"Visibility must be public, unlisted or private."),
			// Rules of the web UI, raised by triggers (check_violation named after the trigger)
			invalid("playlist_visibility_derived", ErrorCodes.VISIBILITY_FOLLOWS_FAVORITES, "visibility",
					VISIBILITY_MESSAGE),
			invalid("playlist_follow_limit", ErrorCodes.FAVORITE_LIMIT_REACHED, null,
					"You already have 5 favorite playlists. Remove one, or replace it (send \"replaces\")."),
			invalid("playlist_track_limit", ErrorCodes.PLAYLIST_FULL, "track_ids", "A playlist holds at most 20 tracks."),
			invalid("playlist_owner_limit", ErrorCodes.PLAYLIST_LIMIT_REACHED, null,
					"You already own 20 playlists, the most you can have. Delete one to make another."),
			invalid("playlist_track_track_id_fkey", "unknown_track", "track_ids", "One or more tracks don't exist."),
			invalid("playlist_collaborator_role_fkey", "unknown_role", "role", "Role must be editor or viewer."),
			invalid("user_saved_track_track_id_fkey", "unknown_track", "track_id", "That track doesn't exist."),
			invalid("user_saved_album_album_id_fkey", "unknown_album", "album_id", "That album doesn't exist."),
			// Artists
			conflict("artist_slug_key", "slug_taken", "slug", "Another artist already uses that slug."),
			conflict("artist_person_id_key", "person_taken", "person_id", "That person is already linked to another artist."),
			conflict("artist_musicbrainz_id_key", "musicbrainz_id_taken", "musicbrainz_id",
					"Another artist already has that MusicBrainz ID."),
			invalid("artist_artist_type_fkey", "unknown_artist_type", "artist_type",
					"Unknown artist type (see GET /api/v1/reference/artist-types)."),
			invalid("artist_country_code_fkey", "unknown_country", "country_code", "Unknown country code."),
			invalid("artist_person_id_fkey", "unknown_person", "person_id", "That person doesn't exist."),
			invalid("artist_person_check", "invalid_person", "person_id", "Only solo artists can be linked to a person."),
			invalid("artist_years_check", "invalid_years", "disbanded_year", "disbanded_year can't be before formed_year."),
			// Albums
			conflict("album_artist_title_key", "album_exists", "title",
					"This artist already has an album with that title and edition."),
			conflict("album_artist_slug_key", "slug_taken", "slug", "This artist already has an album with that slug."),
			conflict("album_upc_key", "upc_taken", "upc", "Another album already has that UPC."),
			conflict("album_musicbrainz_id_key", "musicbrainz_id_taken", "musicbrainz_id",
					"Another album already has that MusicBrainz ID."),
			conflict("album_artist_pkey", "duplicate_artist", "artists", "An artist is listed more than once."),
			conflict("album_genre_pkey", "duplicate_genre", "genres", "A genre is listed more than once."),
			invalid("album_artist_id_fkey", "unknown_artist", "artist_id", "That artist doesn't exist."),
			invalid("album_label_id_fkey", "unknown_label", "label_id", "That label doesn't exist."),
			invalid("album_album_type_fkey", "unknown_album_type", "album_type",
					"Unknown album type (see GET /api/v1/reference/album-types)."),
			invalid("album_upc_check", "invalid_upc", "upc", "A UPC has 12 to 14 digits."),
			invalid("album_release_year_check", "invalid_release_year", "release_year",
					"release_year must match the year of release_date."),
			invalid("album_artist_artist_id_fkey", "unknown_artist", "artists", "One or more artists don't exist."),
			invalid("album_artist_role_fkey", "unknown_artist_role", "artists",
					"Unknown artist role (see GET /api/v1/reference/artist-roles)."),
			invalid("album_genre_genre_id_fkey", "unknown_genre", "genres", "One or more genres don't exist."),
			invalid("album_genre_primary_key", "multiple_primary_genres", "genres", "An album has exactly one primary genre."),
			invalid("track_album_genre_fkey", "genre_in_use", "genres",
					"A genre that tracks of this album use can't be removed from it."),
			// Tracks, songs and credits
			conflict("track_album_position_key", "track_number_taken", "tracks",
					"The album already has a track with that disc and track number."),
			conflict("track_isrc_key", "isrc_taken", "isrc", "Another track already has that ISRC."),
			invalid("track_milliseconds_check", "invalid_duration", "milliseconds", "milliseconds must be positive."),
			invalid("track_disc_number_check", "invalid_disc_number", "disc_number", "disc_number must be positive."),
			invalid("track_track_number_check", "invalid_track_number", "track_number", "track_number must be positive."),
			invalid("track_isrc_check", "invalid_isrc", "isrc", "An ISRC looks like GBAYE0601498."),
			invalid("track_song_id_fkey", "unknown_song", "song_id", "That song doesn't exist."),
			conflict("credit_song_person_role_key", "duplicate_credit", "credits",
					"That person already has that role on this song."),
			invalid("credit_person_id_fkey", "unknown_person", "credits", "One or more people don't exist."),
			invalid("credit_role_fkey", "unknown_credit_role", "credits",
					"Unknown credit role (see GET /api/v1/reference/credit-roles)."));

	private DatabaseErrors() {
	}

	/**
	 * Returns the API error for a database error the client caused (a violated
	 * constraint or a rule enforced by a trigger), or empty for anything else, which is
	 * a server-side problem.
	 */
	public static Optional<ApiException> translate(Throwable ex) {
		PSQLException psql = findPsqlException(ex);
		if (psql == null) {
			return Optional.empty();
		}
		ServerErrorMessage server = psql.getServerErrorMessage();
		String constraint = (server != null) ? server.getConstraint() : null;
		String dbMessage = (server != null && server.getMessage() != null) ? server.getMessage() : psql.getMessage();
		Known known = (constraint != null) ? KNOWN.get(constraint) : null;
		if (known != null) {
			return Optional.of(known.toException());
		}
		String state = psql.getSQLState();
		if (state == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(switch (state) {
			case UNIQUE_VIOLATION -> ApiException.conflict(ErrorCodes.CONFLICT,
					"Another record already has one of these values.", null);
			case FOREIGN_KEY_VIOLATION -> ApiException.unprocessable("invalid_reference",
					"The request refers to something that doesn't exist.");
			case CHECK_VIOLATION, EXCLUSION_VIOLATION -> ApiException.unprocessable("constraint_violation",
					"A value is out of range or has the wrong format.");
			case NOT_NULL_VIOLATION -> ApiException.unprocessable("missing_value", "A required value is missing.");
			// Raised by the schema's triggers with messages written for people, e.g.
			// "user 2 owns playlist 20 and can't also be a collaborator".
			case INTEGRITY_CONSTRAINT_VIOLATION -> ApiException.unprocessable("integrity_violation", dbMessage);
			case INSUFFICIENT_PRIVILEGE -> ApiException.forbidden(dbMessage);
			case "22001" -> ApiException.unprocessable("value_too_long", "A value is too long.");
			case "22003" -> ApiException.unprocessable("value_out_of_range", "A number is out of range.");
			case "22007", "22008" -> ApiException.unprocessable("invalid_date", "A date is invalid.");
			case "22P02" -> ApiException.unprocessable("invalid_value", "A value has the wrong format.");
			default -> null;
		});
	}

	/** Whether the error is a violation of the named constraint. */
	public static boolean isViolation(Throwable ex, String constraintName) {
		PSQLException psql = findPsqlException(ex);
		ServerErrorMessage server = (psql != null) ? psql.getServerErrorMessage() : null;
		return server != null && constraintName.equals(server.getConstraint());
	}

	private static @Nullable PSQLException findPsqlException(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof PSQLException psql) {
				return psql;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return null;
	}

	private static Map.Entry<String, Known> conflict(String constraint, String code, @Nullable String field, String message) {
		return Map.entry(constraint, new Known(HttpStatus.CONFLICT, code, field, message));
	}

	private static Map.Entry<String, Known> invalid(String constraint, String code, @Nullable String field, String message) {
		return Map.entry(constraint, new Known(HttpStatus.UNPROCESSABLE_CONTENT, code, field, message));
	}

	private record Known(HttpStatus status, String code, @Nullable String field, String message) {

		ApiException toException() {
			return (this.status == HttpStatus.CONFLICT) ? ApiException.conflict(this.code, this.message, this.field)
					: ApiException.unprocessable(this.code, this.message, this.field);
		}

	}

}
