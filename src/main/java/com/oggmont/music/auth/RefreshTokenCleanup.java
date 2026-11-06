package com.oggmont.music.auth;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes refresh tokens that expired more than a day ago, once a day.
 */
@Component
class RefreshTokenCleanup {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

	private final RefreshTokenRepository tokens;

	private final Clock clock;

	RefreshTokenCleanup(RefreshTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.jwt.cleanup-cron:0 30 3 * * *}")
	void deleteExpiredTokens() {
		int deleted = this.tokens.deleteExpiredBefore(this.clock.instant().minus(Duration.ofDays(1)));
		if (deleted > 0) {
			log.info("Deleted {} expired refresh tokens", deleted);
		}
	}

}
