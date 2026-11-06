package com.oggmont.music.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	@Test
	void fieldPathsUseTheJsonNames() {
		assertThat(GlobalExceptionHandler.fieldName("displayName")).isEqualTo("display_name");
		assertThat(GlobalExceptionHandler.fieldName("trackIds[0]")).isEqualTo("track_ids[0]");
		assertThat(GlobalExceptionHandler.fieldName("genres[1].genreId")).isEqualTo("genres[1].genre_id");
		assertThat(GlobalExceptionHandler.fieldName("value.coverUrl")).as("PartialUpdate prefix").isEqualTo("cover_url");
		assertThat(GlobalExceptionHandler.fieldName("limit")).isEqualTo("limit");
	}

}
