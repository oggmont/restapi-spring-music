package com.oggmont.music.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A {@link Clock} bean, so token lifetimes can be tested with a fixed clock.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
