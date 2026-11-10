package com.oggmont.music.common.paging;

import java.util.List;

/**
 * A random sample: {@code { "items": [...] }}. Not paged and without a total, because
 * every call draws a new sample; clients send the IDs they already have as
 * {@code ?exclude=} to get different ones.
 *
 * @param <T> the item type
 * @param items the sampled items
 */
public record Sample<T>(List<T> items) {

	public Sample {
		items = List.copyOf(items);
	}

}
