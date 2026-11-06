package com.oggmont.music.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DurationsTest {

	@Test
	void formatsLikeTheTrackDetailView() {
		assertThat(Durations.format(185_000)).isEqualTo("3:05");
		assertThat(Durations.format(260_999)).as("seconds are truncated").isEqualTo("4:20");
		assertThat(Durations.format(0)).isEqualTo("0:00");
	}

	@Test
	void showsHoursForLongTracks() {
		assertThat(Durations.format(3_725_000)).isEqualTo("1:02:05");
	}

}
