package com.oggmont.music.common.paging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;

/**
 * {@code ?limit=} (default 20, max 100) and {@code ?offset=} query parameters of list
 * endpoints. Bound from the query string by Spring MVC and validated with {@code @Valid}.
 *
 * @param limit requested page size, or null for the default
 * @param offset requested number of items to skip, or null for none
 */
@ParameterObject
public record PageParams(@Nullable @Min(1) @Max(MAX_LIMIT) Integer limit, @Nullable @Min(0) Integer offset) {

	public static final int DEFAULT_LIMIT = 20;

	public static final int MAX_LIMIT = 100;

	public static PageParams of(int limit, int offset) {
		return new PageParams(limit, offset);
	}

	public int resolvedLimit() {
		return (this.limit != null) ? this.limit : DEFAULT_LIMIT;
	}

	public int resolvedOffset() {
		return (this.offset != null) ? this.offset : 0;
	}

}
