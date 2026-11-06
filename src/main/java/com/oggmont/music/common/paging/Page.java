package com.oggmont.music.common.paging;

import java.util.List;
import java.util.function.Function;

/**
 * A page of a list: {@code { "items": [...], "limit": 20, "offset": 0, "total": 193 }}.
 *
 * @param <T> the item type
 * @param items the items on this page
 * @param limit the page size that was applied
 * @param offset how many items were skipped
 * @param total how many items there are in all
 */
public record Page<T>(List<T> items, int limit, int offset, long total) {

	public Page {
		items = List.copyOf(items);
	}

	public static <T> Page<T> of(List<T> items, PageParams params, long total) {
		return new Page<>(items, params.resolvedLimit(), params.resolvedOffset(), total);
	}

	public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
		return new Page<>(this.items.stream().<R>map(mapper).toList(), this.limit, this.offset, this.total);
	}

}
