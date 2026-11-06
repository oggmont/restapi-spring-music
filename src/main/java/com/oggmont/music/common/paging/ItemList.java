package com.oggmont.music.common.paging;

import java.util.List;

/**
 * A short, unpaginated list: {@code { "items": [...], "total": 17 }}.
 *
 * @param <T> the item type
 * @param items all the items
 * @param total how many there are
 */
public record ItemList<T>(List<T> items, int total) {

	public ItemList {
		items = List.copyOf(items);
	}

	public static <T> ItemList<T> of(List<T> items) {
		return new ItemList<>(items, items.size());
	}

}
