package com.oggmont.music.common;

/**
 * Display strings for running times.
 */
public final class Durations {

	private Durations() {
	}

	/**
	 * {@code 185000} -> {@code "3:05"}; an hour or more -> {@code "1:02:05"}. Seconds are
	 * truncated, like the {@code duration} column of the {@code track_detail} view.
	 */
	public static String format(long milliseconds) {
		long totalSeconds = Math.max(0, milliseconds) / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return (hours > 0) ? "%d:%02d:%02d".formatted(hours, minutes, seconds) : "%d:%02d".formatted(minutes, seconds);
	}

}
