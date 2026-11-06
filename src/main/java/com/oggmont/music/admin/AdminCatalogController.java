package com.oggmont.music.admin;

import com.oggmont.music.catalog.AlbumRepository;
import com.oggmont.music.catalog.ArtistRepository;
import com.oggmont.music.catalog.SongRepository;
import com.oggmont.music.common.ApiPaths;
import com.oggmont.music.common.paging.ItemList;
import com.oggmont.music.common.web.PartialUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalog administration (API-Design.md, "Catalog administration"). Needs the curator
 * role (admins have it too).
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('CURATOR')")
@Tag(name = "Catalog administration", description = "Curators and admins: edit artists, albums, tracks and credits")
class AdminCatalogController {

	private final AdminCatalogService catalog;

	AdminCatalogController(AdminCatalogService catalog) {
		this.catalog = catalog;
	}

	@PostMapping("/artists")
	@Operation(summary = "Create an artist (the slug is generated when omitted)")
	ResponseEntity<ArtistRepository.ArtistDetail> createArtist(@Valid @RequestBody AdminRequests.CreateArtistRequest request) {
		ArtistRepository.ArtistDetail artist = this.catalog.createArtist(request);
		return ResponseEntity.created(URI.create(ApiPaths.BASE + "/artists/" + artist.slug())).body(artist);
	}

	@PatchMapping("/artists/{id}")
	@Operation(summary = "Edit an artist (the slug only changes when you send one)")
	ArtistRepository.ArtistDetail updateArtist(@PathVariable int id,
			@Valid @RequestBody PartialUpdate<AdminRequests.UpdateArtistRequest> request) {
		return this.catalog.updateArtist(id, request);
	}

	@PostMapping("/albums")
	@Operation(summary = "Create an album with its artists and genres (one transaction)")
	ResponseEntity<AlbumRepository.AlbumDetail> createAlbum(@Valid @RequestBody AdminRequests.CreateAlbumRequest request) {
		AlbumRepository.AlbumDetail album = this.catalog.createAlbum(request);
		return ResponseEntity.created(URI.create(ApiPaths.BASE + "/albums/" + album.albumId())).body(album);
	}

	@PostMapping("/albums/{id}/tracks")
	@Operation(summary = "Add tracks to an album; returns the album's track listing")
	ResponseEntity<ItemList<AlbumRepository.AlbumTrack>> addTracks(@PathVariable int id,
			@Valid @RequestBody AdminRequests.AddAlbumTracksRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ItemList.of(this.catalog.addTracks(id, request)));
	}

	@PutMapping("/albums/{id}/genres")
	@Operation(summary = "Replace an album's genres (exactly one primary)")
	Map<String, Object> setGenres(@PathVariable int id, @Valid @RequestBody AdminRequests.SetGenresRequest request) {
		List<AdminCatalogRepository.AlbumGenreRow> genres = this.catalog.setGenres(id, request);
		return Map.of("album_id", id, "genres", genres);
	}

	@PostMapping("/songs/{id}/credits")
	@Operation(summary = "Add songwriting credits (composer text regenerates automatically)")
	ResponseEntity<SongRepository.SongDetail> addCredits(@PathVariable int id,
			@Valid @RequestBody AdminRequests.AddCreditsRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.catalog.addCredits(id, request));
	}

}
