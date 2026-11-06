package com.oggmont.music.common.jdbc;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The {@code SET} clause of a partial update (PATCH): one named parameter per changed
 * column. Column names must be constants from the calling code, never request input.
 */
public final class ColumnChanges {

	private final List<String> assignments = new ArrayList<>();

	private final List<Param> params = new ArrayList<>();

	/** Sets {@code column} to {@code value} (which may be null), bound with the given SQL type. */
	public ColumnChanges set(String column, @Nullable Object value, int sqlType) {
		this.assignments.add(column + " = :" + column);
		this.params.add(new Param(column, value, sqlType));
		return this;
	}

	public boolean isEmpty() {
		return this.assignments.isEmpty();
	}

	/** {@code name = :name, bio = :bio}. */
	public String setClause() {
		return String.join(", ", this.assignments);
	}

	/** Binds the values of the changed columns. */
	public JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement) {
		this.params.forEach((param) -> statement.param(param.name(), param.value(), param.sqlType()));
		return statement;
	}

	private record Param(String name, @Nullable Object value, int sqlType) {
	}

}
