package com.oggmont.music.common.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Null-safe {@link ResultSet} accessors for the column types the schema uses.
 */
public final class Rows {

	private Rows() {
	}

	public static @Nullable Integer integer(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, Integer.class);
	}

	public static @Nullable Long longValue(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, Long.class);
	}

	public static @Nullable Boolean bool(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, Boolean.class);
	}

	public static @Nullable String string(ResultSet rs, String column) throws SQLException {
		return rs.getString(column);
	}

	/** A {@code timestamptz} column as an instant (serialized as ISO 8601 in UTC). */
	public static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value != null) ? value.toInstant() : null;
	}

	public static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
		Instant value = instant(rs, column);
		if (value == null) {
			throw new SQLException("Column " + column + " is null");
		}
		return value;
	}

	/** An instant as a {@code timestamptz} query parameter (PgJDBC binds OffsetDateTime natively). */
	public static OffsetDateTime param(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	public static @Nullable LocalDate date(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, LocalDate.class);
	}

	public static @Nullable UUID uuid(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, UUID.class);
	}

	/** A {@code text[]} or {@code varchar[]} column; NULL becomes an empty list. */
	public static List<String> strings(ResultSet rs, String column) throws SQLException {
		Array array = rs.getArray(column);
		if (array == null) {
			return List.of();
		}
		try {
			Object[] values = (Object[]) array.getArray();
			return Arrays.stream(values).map(String::valueOf).toList();
		}
		finally {
			array.free();
		}
	}

}
