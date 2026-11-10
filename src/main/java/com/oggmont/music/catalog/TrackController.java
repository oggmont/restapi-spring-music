package com.oggmont.music.catalog;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.common.paging.Sample;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tracks (recordings), songs (compositions) and people. Public; read-only.
 */
@RestController
@Tag(name = "Catalog")
class TrackController {

	private final TrackRepository tracks;

	private final SongRepository songs;

	private final PersonRepository people;

	TrackController(TrackRepository tracks, SongRepository songs, PersonRepository people) {
		this.tracks = tracks;
		this.songs = songs;
		this.people = people;
	}

	@GetMapping("/tracks")
	@Operation(summary = "Song search: ?q=daft punk, by track name, artist or album (accents ignored)",
			description = "Full track cards, best first: matches in the track's name rank above matches in its "
					+ "artist or album. `q` needs at least 2 characters (422 otherwise). `is_liked` with a token.")
	TrackRepository.TrackSearchResults search(@RequestParam(required = false) @Nullable @Size(max = 100) String q,
			@CurrentUser @Nullable Caller caller, @Valid PageParams page) {
		String query = (q != null) ? q.strip() : "";
		if (query.codePointCount(0, query.length()) < 2) {
			throw ApiException.unprocessable("query_too_short", "Type at least 2 characters to search.", "q");
		}
		return this.tracks.search(query, (caller != null) ? caller.userId() : null, page.resolvedLimit(),
				page.resolvedOffset());
	}

	@GetMapping("/tracks/random")
	@Operation(summary = "Random tracks from the catalog",
			description = "Each call is a new sample. Send the IDs already on screen as `exclude`, so \"load more\" "
					+ "doesn't repeat them. `is_liked` with a token.")
	Sample<TrackCard> random(@CurrentUser @Nullable Caller caller,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
			@RequestParam(required = false) @Nullable @Size(max = 1000) List<Integer> exclude) {
		Set<Integer> skip = (exclude != null) ? new LinkedHashSet<>(exclude) : Set.of();
		return new Sample<>(this.tracks.random((caller != null) ? caller.userId() : null, limit, skip));
	}

	@GetMapping("/tracks/{id}")
	@Operation(summary = "Track details: song, album, artists, genre, composer, ISRC")
	TrackRepository.TrackDetail track(@PathVariable int id, @CurrentUser @Nullable Caller caller) {
		return this.tracks.find(id, (caller != null) ? caller.userId() : null)
			.orElseThrow(() -> ApiException.notFound("No track " + id + "."));
	}

	@GetMapping("/songs/{id}")
	@Operation(summary = "Song (composition) with its songwriting credits")
	SongRepository.SongDetail song(@PathVariable int id) {
		return this.songs.find(id).orElseThrow(() -> noSong(id));
	}

	@GetMapping("/songs/{id}/recordings")
	@Operation(summary = "Every recording of the song")
	SongRepository.Recordings recordings(@PathVariable int id) {
		SongRepository.SongDetail song = this.songs.find(id).orElseThrow(() -> noSong(id));
		List<SongRepository.Recording> recordings = this.songs.recordings(id);
		return new SongRepository.Recordings(song, recordings, recordings.size());
	}

	@GetMapping("/people/{id}")
	@Operation(summary = "Person: bands, solo artist and songwriting credits")
	PersonRepository.PersonDetail person(@PathVariable int id) {
		return this.people.find(id).orElseThrow(() -> ApiException.notFound("No person " + id + "."));
	}

	private static ApiException noSong(int id) {
		return ApiException.notFound("No song " + id + ".");
	}

}
