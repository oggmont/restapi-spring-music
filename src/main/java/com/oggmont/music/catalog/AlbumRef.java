package com.oggmont.music.catalog;

/**
 * A link to an album: {@code /albums/{album_id}} or
 * {@code /artists/{artist_slug}/albums/{slug}}.
 *
 * @param albumId {@code album.album_id}
 * @param title the album's title
 * @param slug URL key, unique per credited artist
 * @param artistSlug slug of the credited artist
 */
public record AlbumRef(int albumId, String title, String slug, String artistSlug) {
}
