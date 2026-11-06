package com.oggmont.music.config;

import com.oggmont.music.common.error.SecurityErrorHandlers;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer-token security.
 * <p>
 * Reads of the catalog, public profiles and playlists work without a token (services
 * still apply playlist visibility); {@code /me}, every write and {@code /admin} need one.
 * Roles form a hierarchy: {@code admin > curator > listener}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

	/** Public reads, under {@code /api/{version}}. */
	private static final String[] PUBLIC_GETS = { api("/users/*"), api("/users/*/**"), api("/playlists/**"),
			api("/artists/**"), api("/albums/**"), api("/tracks/**"), api("/songs/**"), api("/people/**"),
			api("/genres/**"), api("/labels/**"), api("/search"), api("/reference/**") };

	/** Sign-up and the token endpoints. */
	private static final String[] PUBLIC_POSTS = { api("/users"), api("/auth/login"), api("/auth/refresh"),
			api("/auth/logout") };

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> callerAuthenticationConverter, SecurityErrorHandlers errors) {
		http.csrf(AbstractHttpConfigurer::disable) // bearer tokens, no cookies: nothing to forge
			.cors((cors) -> {
			})
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests((requests) -> requests.requestMatchers("/error")
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
				.permitAll()
				.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, PUBLIC_GETS)
				.permitAll()
				.requestMatchers(HttpMethod.POST, PUBLIC_POSTS)
				.permitAll()
				.requestMatchers(api("/admin/users/**"))
				.hasRole("ADMIN")
				.requestMatchers(api("/admin/**"))
				.hasRole("CURATOR")
				.requestMatchers("/actuator/**")
				.hasRole("ADMIN")
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer((oauth2) -> oauth2
				.jwt((jwt) -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(callerAuthenticationConverter))
				.authenticationEntryPoint(errors)
				.accessDeniedHandler(errors))
			.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors));
		return http.build();
	}

	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
			.role("ADMIN")
			.implies("CURATOR")
			.role("CURATOR")
			.implies("LISTENER")
			.build();
	}

	/** bcrypt by default, with an {id} prefix so the algorithm can be upgraded later. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(properties.allowedOrigins());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-Match", "If-None-Match"));
		cors.setExposedHeaders(List.of("ETag", "Location", "WWW-Authenticate"));
		cors.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		return source;
	}

	private static String api(String path) {
		return "/api/*" + path;
	}

	/**
	 * @param allowedOrigins browser origins allowed to call the API (comma-separated in
	 * {@code APP_CORS_ALLOWED_ORIGINS})
	 */
	@ConfigurationProperties("app.cors")
	record CorsProperties(@DefaultValue List<String> allowedOrigins) {
	}

}
