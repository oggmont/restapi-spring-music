package com.oggmont.music.catalog;

import com.oggmont.music.auth.Caller;
import com.oggmont.music.auth.CurrentUser;
import com.oggmont.music.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
