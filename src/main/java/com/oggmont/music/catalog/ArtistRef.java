package com.oggmont.music.catalog;

/**
 * A link to an artist page: {@code /artists/{slug}}.
 *
 * @param artistId {@code artist.artist_id}
 * @param name the artist's name
 * @param slug URL key
 */
public record ArtistRef(int artistId, String name, String slug) {
}
