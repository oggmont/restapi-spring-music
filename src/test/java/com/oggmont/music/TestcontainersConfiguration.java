package com.oggmont.music;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * A throwaway PostgreSQL 17 loaded with the real music-db schema and seed data, from the
 * init scripts of the db-postgresql-music project. By default they are read from the
 * sibling checkout {@code ../db-postgresql-music/db/init}; set {@code MUSIC_DB_INIT_DIR}
 * (or {@code -Dmusicdb.init-dir=...}) to use another location.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String POSTGRES_IMAGE = "postgres:17-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE)).withDatabaseName("music_db")
			.withUsername("music")
			.withPassword("music")
			.withCopyFileToContainer(MountableFile.forHostPath(initScripts()), "/docker-entrypoint-initdb.d/")
			.withStartupTimeout(Duration.ofMinutes(3));
	}

	static Path initScripts() {
		String configured = System.getProperty("musicdb.init-dir", System.getenv("MUSIC_DB_INIT_DIR"));
		Path directory = Path.of((configured != null) ? configured : "../db-postgresql-music/db/init")
			.toAbsolutePath()
			.normalize();
		if (!Files.isRegularFile(directory.resolve("01_schema.sql"))) {
			throw new IllegalStateException("Can't find the music-db init scripts in " + directory
					+ ". Clone https://github.com/oggmont/db-postgresql-music next to this project, or set "
					+ "MUSIC_DB_INIT_DIR to its db/init directory.");
		}
		return directory;
	}

}
