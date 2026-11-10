package com.oggmont.music.follow;

import com.oggmont.music.catalog.ArtistCard;
import com.oggmont.music.catalog.CatalogSql;
import com.oggmont.music.common.jdbc.Rows;
import com.oggmont.music.common.paging.Page;
import com.oggmont.music.common.paging.PageParams;
import com.oggmont.music.playlist.PlaylistSql;
import com.oggmont.music.playlist.PlaylistSummary;
import com.oggmont.music.user.UserCard;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Follows of playlists, artists and users. Follower counts are kept by triggers, so the
 * API never touches them.
 */
@Repository
class FollowRepository {

	private final JdbcClient jdbc;

	FollowRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	boolean canViewPlaylist(int playlistId, @Nullable Integer me) {
		return this.jdbc.sql("SELECT playlist_can_view(:playlistId, :me)")
			.param("playlistId", playlistId)
			.param("me", me, Types.INTEGER)
			.query(Boolean.class)
			.single();
	}

	Optional<Integer> artistId(String slug) {
		return this.jdbc.sql("SELECT artist_id FROM artist WHERE slug = :slug")
			.param("slug", slug)
			.query(Integer.class)
			.optional();
	}

	Optional<Integer> userId(String username) {
		return this.jdbc.sql("SELECT user_id FROM app_user WHERE username = :username")
			.param("username", username)
			.query(Integer.class)
			.optional();
	}

	boolean isFavorite(int me, int playlistId) {
		return this.jdbc.sql("SELECT EXISTS (SELECT 1 FROM playlist_follow WHERE user_id = :me AND playlist_id = :id)")
			.param("me", me)
			.param("id", playlistId)
			.query(Boolean.class)
			.single();
	}

	/**
	 * Adds a favorite. A sixth fails with {@code playlist_follow_limit}, also when the row
	 * already exists (the trigger runs before {@code ON CONFLICT}), so check
	 * {@link #isFavorite} first. Triggers make the playlist public and count the follower.
	 */
	void followPlaylist(int me, int playlistId) {
		this.jdbc.sql("INSERT INTO playlist_follow (user_id, playlist_id) VALUES (:me, :id) ON CONFLICT DO NOTHING")
			.param("me", me)
			.param("id", playlistId)
			.update();
	}

	/** Removes a favorite; returns whether there was one. The last one makes the playlist private (trigger). */
	boolean unfollowPlaylist(int me, int playlistId) {
		return this.jdbc.sql("DELETE FROM playlist_follow WHERE user_id = :me AND playlist_id = :id")
			.param("me", me)
			.param("id", playlistId)
			.update() > 0;
	}

	/** A playlist's visibility and follower count, and the caller's favorites, as triggers left them. */
	Optional<FavoriteState> favoriteState(int me, int playlistId) {
		return this.jdbc.sql("""
				SELECT p.playlist_id, p.visibility, p.follower_count,
				       EXISTS (SELECT 1 FROM playlist_follow f WHERE f.playlist_id = p.playlist_id AND f.user_id = :me)
				           AS is_favorite,
				       (SELECT count(*) FROM playlist_follow f WHERE f.user_id = :me) AS my_favorite_count
				FROM playlist p
				WHERE p.playlist_id = :id
				""")
			.param("me", me)
			.param("id", playlistId)
			.query((rs, rowNum) -> new FavoriteState(rs.getInt("playlist_id"), rs.getBoolean("is_favorite"),
					rs.getString("visibility"), rs.getInt("follower_count"), rs.getInt("my_favorite_count"), null))
			.optional();
	}

	/** A user's favorites (at most 5), A-Z or in the order they were added. */
	List<FavoritePlaylist> favoritesOf(int userId, FavoriteSort sort) {
		return this.jdbc.sql("""
				SELECT f.followed_at, %s
				FROM playlist_follow f
				JOIN playlist_summary ps ON ps.playlist_id = f.playlist_id
				WHERE f.user_id = :userId
				ORDER BY %s
				""".formatted(PlaylistSql.SUMMARY_COLUMNS, sort.orderBy))
			.param("userId", userId)
			.query((rs, rowNum) -> new FavoritePlaylist(PlaylistSql.summary(rs),
					Rows.requiredInstant(rs, "followed_at")))
			.list();
	}

	void followArtist(int me, int artistId) {
		this.jdbc.sql("INSERT INTO artist_follow (user_id, artist_id) VALUES (:me, :id) ON CONFLICT DO NOTHING")
			.param("me", me)
			.param("id", artistId)
			.update();
	}

	void unfollowArtist(int me, int artistId) {
		this.jdbc.sql("DELETE FROM artist_follow WHERE user_id = :me AND artist_id = :id")
			.param("me", me)
			.param("id", artistId)
			.update();
	}

	/** Following yourself fails with {@code user_follow_self_check} (422). */
	void followUser(int me, int userId) {
		this.jdbc.sql("INSERT INTO user_follow (follower_id, followed_id) VALUES (:me, :id) ON CONFLICT DO NOTHING")
			.param("me", me)
			.param("id", userId)
			.update();
	}

	void unfollowUser(int me, int userId) {
		this.jdbc.sql("DELETE FROM user_follow WHERE follower_id = :me AND followed_id = :id")
			.param("me", me)
			.param("id", userId)
			.update();
	}

	Page<FollowedArtist> followedArtists(int me, PageParams page) {
		List<FollowedArtist> items = this.jdbc.sql("""
				WITH page AS (
				    SELECT artist_id, followed_at FROM artist_follow
				    WHERE user_id = :me
				    ORDER BY followed_at DESC, artist_id
				    LIMIT :limit OFFSET :offset
				)
				SELECT page.followed_at, """ + CatalogSql.ARTIST_CARD_COLUMNS + """

				FROM page
				JOIN artist_summary ars ON ars.artist_id = page.artist_id
				ORDER BY page.followed_at DESC, page.artist_id
				""")
			.param("me", me)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new FollowedArtist(Rows.requiredInstant(rs, "followed_at"),
					CatalogSql.artistCard(rs)))
			.list();
		long total = this.jdbc.sql("SELECT count(*) FROM artist_follow WHERE user_id = :me")
			.param("me", me)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	/**
	 * Followed playlists (favorites) the caller can still view. A favorite keeps its
	 * playlist public, so in practice that is all of them.
	 */
	Page<FollowedPlaylist> followedPlaylists(int me, FavoriteSort sort, PageParams page) {
		String visible = """
				WITH visible AS (
				    SELECT playlist_id, followed_at FROM playlist_follow
				    WHERE user_id = :me AND playlist_can_view(playlist_id, :me)
				)
				""";
		List<FollowedPlaylist> items = this.jdbc.sql(visible + """
				, page AS (
				    SELECT f.playlist_id, f.followed_at, row_number() OVER (ORDER BY %1$s) AS n
				    FROM visible f
				    JOIN playlist p ON p.playlist_id = f.playlist_id
				    ORDER BY %1$s
				    LIMIT :limit OFFSET :offset
				)
				SELECT page.followed_at, %2$s
				FROM page
				JOIN playlist_summary ps ON ps.playlist_id = page.playlist_id
				ORDER BY page.n
				""".formatted(sort.newestFirst, PlaylistSql.SUMMARY_COLUMNS))
			.param("me", me)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new FollowedPlaylist(Rows.requiredInstant(rs, "followed_at"),
					PlaylistSql.summary(rs)))
			.list();
		long total = this.jdbc.sql(visible + "SELECT count(*) FROM visible").param("me", me).query(Long.class).single();
		return Page.of(items, page, total);
	}

	Page<UserCard> playlistFollowers(int playlistId, PageParams page) {
		List<UserCard> items = this.jdbc.sql("""
				SELECT u.user_id, u.username, u.display_name, u.avatar_url, u.is_verified, u.follower_count, f.followed_at
				FROM playlist_follow f
				JOIN app_user u ON u.user_id = f.user_id
				WHERE f.playlist_id = :playlistId
				ORDER BY f.followed_at DESC, u.username
				LIMIT :limit OFFSET :offset
				""")
			.param("playlistId", playlistId)
			.param("limit", page.resolvedLimit())
			.param("offset", page.resolvedOffset())
			.query((rs, rowNum) -> new UserCard(rs.getInt("user_id"), rs.getString("username"),
					rs.getString("display_name"), rs.getString("avatar_url"), rs.getBoolean("is_verified"),
					rs.getInt("follower_count"), Rows.instant(rs, "followed_at")))
			.list();
		long total = this.jdbc.sql("SELECT count(*) FROM playlist_follow WHERE playlist_id = :playlistId")
			.param("playlistId", playlistId)
			.query(Long.class)
			.single();
		return Page.of(items, page, total);
	}

	/**
	 * @param followedAt when the caller followed the artist
	 * @param artist the artist
	 */
	record FollowedArtist(Instant followedAt, ArtistCard artist) {
	}

	/**
	 * @param followedAt when the caller followed the playlist
	 * @param playlist the playlist
	 */
	record FollowedPlaylist(Instant followedAt, PlaylistSummary playlist) {
	}

	/** Orders of favorites lists, over {@code playlist_follow f} and {@code playlist p} / {@code playlist_summary ps}. */
	enum FavoriteSort {

		/** A-Z, ignoring accents and case. */
		NAME("f_search_key(ps.name), f.playlist_id", "f_search_key(p.name), f.playlist_id"),

		/** By when they were added: oldest first on profiles, newest first on {@code /me/following/playlists}. */
		FOLLOWED_AT("f.followed_at, f.playlist_id", "f.followed_at DESC, f.playlist_id");

		final String orderBy;

		final String newestFirst;

		FavoriteSort(String orderBy, String newestFirst) {
			this.orderBy = orderBy;
			this.newestFirst = newestFirst;
		}

	}

}
