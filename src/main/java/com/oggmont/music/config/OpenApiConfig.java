package com.oggmont.music.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.oggmont.music.common.web.PartialUpdate;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Iterator;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.oggmont.music.common.ApiPaths.CURRENT_VERSION;

/**
 * OpenAPI document served at {@code /v3/api-docs}, with Swagger UI at {@code /swagger-ui.html}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	static final String BEARER_SCHEME = "bearer";

	@Bean
	OpenAPI musicApiOpenApi() {
		return new OpenAPI()
			.info(new Info().title("music-db API")
				.version("v" + CURRENT_VERSION)
				.description("""
						REST API for the music-db playlist platform: catalog, users, playlists, \
						libraries and follows. Get a token with POST /api/v1/auth/login and send it as \
						`Authorization: Bearer <token>`. Catalog and public-playlist reads work without one."""))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}

	/**
	 * swagger-core still introspects models with Jackson 2, so it doesn't see the Jackson 3
	 * {@code spring.jackson.property-naming-strategy}. Without this the documented schemas
	 * would be camelCase while the API speaks snake_case.
	 */
	@Bean
	ModelResolver snakeCaseModelResolver() {
		ObjectMapper mapper = Json.mapper().copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		return new ModelResolver(mapper) {

			/** A {@code PartialUpdate<T>} body is documented as {@code T}: that is what clients send. */
			@Override
			public Schema resolve(AnnotatedType annotatedType, ModelConverterContext context,
					Iterator<ModelConverter> next) {
				JavaType type = this._mapper.constructType(annotatedType.getType());
				if (type != null && PartialUpdate.class.equals(type.getRawClass()) && type.containedTypeCount() == 1) {
					return context.resolve(new AnnotatedType(type.containedType(0)).resolveAsRef(true));
				}
				return super.resolve(annotatedType, context, next);
			}

		};
	}

	/**
	 * Controllers are mapped under {@code /api/{version}} (Spring Framework API versioning).
	 * Document the concrete paths of the current version so "Try it out" works.
	 */
	@Bean
	OpenApiCustomizer concreteVersionPaths() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			Paths paths = new Paths();
			openApi.getPaths()
				.forEach((path, item) -> paths.addPathItem(path.replace("/{version}/", "/v" + CURRENT_VERSION + "/"), item));
			openApi.setPaths(paths);
		};
	}

}
