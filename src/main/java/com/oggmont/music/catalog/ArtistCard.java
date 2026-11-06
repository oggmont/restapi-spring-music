package com.oggmont.music.catalog;

import org.jspecify.annotations.Nullable;

/**
 * An artist as it appears in lists, from the {@code artist_summary} view.
 *
 * @param artistId {@code artist.artist_id}
 * @param name the artist's name
 * @param slug URL key
 * @param artistType solo, group, orchestra...
 * @param countryCode ISO 3166-1 alpha-2
 * @param formedYear year formed (or debut)
 * @param disbandedYear year disbanded
 * @param imageUrl picture
 * @param followerCount number of followers
 * @param albumCount albums credited to them or listing them
 * @param trackCount tracks on those albums, plus track appearances
 */
public record ArtistCard(int artistId, String name, String slug, @Nullable String artistType,
		@Nullable String countryCode, @Nullable Integer formedYear, @Nullable Integer disbandedYear,
		@Nullable String imageUrl, int followerCount, long albumCount, long trackCount) {
}
