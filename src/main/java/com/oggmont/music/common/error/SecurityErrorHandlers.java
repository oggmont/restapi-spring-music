package com.oggmont.music.common.error;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 and 403 responses from Spring Security, as {@link ApiError} bodies. The bearer-token
 * handlers they delegate to set the status and the {@code WWW-Authenticate} header
 * (RFC 6750).
 */
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

	private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();

	private final JsonMapper jsonMapper;

	SecurityErrorHandlers(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException, ServletException {
		this.bearerEntryPoint.commence(request, response, ex);
		ApiError body = (ex instanceof OAuth2AuthenticationException)
				? ApiError.of(ErrorCodes.INVALID_TOKEN,
						"The access token is invalid or has expired. Refresh it or sign in again.")
				: ApiError.of(ErrorCodes.UNAUTHORIZED,
						"Sign in first: send 'Authorization: Bearer <token>' (POST /api/v1/auth/login returns one).");
		write(response, body);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException, ServletException {
		this.bearerAccessDeniedHandler.handle(request, response, ex);
		write(response, ApiError.of(ErrorCodes.FORBIDDEN, "You don't have permission to do this."));
	}

	private void write(HttpServletResponse response, ApiError body) throws IOException {
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
