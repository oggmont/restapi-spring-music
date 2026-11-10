package com.oggmont.music.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps the {@code track_search} materialized view (behind {@code GET /tracks?q=})
 * current after catalog writes. The view is refreshed {@code CONCURRENTLY}, so searches
 * keep working while it rebuilds.
 */
@Component
public class TrackSearchIndex {

	private static final Logger logger = LoggerFactory.getLogger(TrackSearchIndex.class);

	private final JdbcClient jdbc;

	TrackSearchIndex(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Refreshes the view: after the current transaction commits when there is one, so it
	 * sees the change, otherwise right away. A failed refresh is logged, not thrown: the
	 * catalog write itself succeeded.
	 */
	public void refresh() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

				@Override
				public void afterCommit() {
					refreshNow();
				}

			});
		}
		else {
			refreshNow();
		}
	}

	private void refreshNow() {
		try {
			this.jdbc.sql("REFRESH MATERIALIZED VIEW CONCURRENTLY track_search").update();
		}
		catch (RuntimeException ex) {
			logger.warn("Couldn't refresh track_search; song search may miss the latest catalog changes", ex);
		}
	}

}
