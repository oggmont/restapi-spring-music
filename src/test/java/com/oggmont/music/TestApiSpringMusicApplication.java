package com.oggmont.music;

import org.springframework.boot.SpringApplication;

/**
 * Runs the API against a throwaway Testcontainers database (schema and seed loaded), so
 * it can be tried without the Docker database running: {@code ./mvnw spring-boot:test-run}.
 */
public class TestApiSpringMusicApplication {

	public static void main(String[] args) {
		SpringApplication.from(ApiSpringMusicApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
