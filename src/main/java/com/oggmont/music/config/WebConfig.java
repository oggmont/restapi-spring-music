package com.oggmont.music.config;

import com.oggmont.music.common.ApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts every API controller under {@code /api/{version}} and lets Spring Framework's API
 * versioning resolve the version from that path segment ({@code v1}, {@code 1} and
 * {@code 1.0} all mean version 1).
 */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(ApiPaths.PREFIX, HandlerTypePredicate.forBasePackage("com.oggmont.music"));
	}

	@Override
	public void configureApiVersioning(ApiVersionConfigurer configurer) {
		configurer.usePathSegment(1).addSupportedVersions(ApiPaths.CURRENT_VERSION);
	}

}
