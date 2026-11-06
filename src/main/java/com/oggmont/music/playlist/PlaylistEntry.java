package com.oggmont.music.playlist;

import com.oggmont.music.catalog.TrackCard;
import com.oggmont.music.user.UserRef;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a playlist. Entries are identified by position, because the same track
 * can appear more than once.
 *
 * @param position 1-based position
 * @param addedAt when it was added
 * @param addedBy who added it; null once that account is deleted
 * @param track the track
 */
public record PlaylistEntry(int position, Instant addedAt, @Nullable UserRef addedBy, TrackCard track) {
}
