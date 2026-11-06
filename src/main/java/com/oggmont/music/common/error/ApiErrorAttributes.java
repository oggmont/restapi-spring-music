package com.oggmont.music.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/**
 * Body of Spring Boot's {@code /error} page, used for errors raised outside Spring MVC
 * (for example in a servlet filter), in the same {@link ApiError} format.
 */
@Component
class ApiErrorAttributes extends DefaultErrorAttributes {

	@Override
	public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
		Map<String, Object> defaults = super.getErrorAttributes(webRequest, ErrorAttributeOptions.defaults());
		HttpStatusCode status = (defaults.get("status") instanceof Integer code) ? HttpStatusCode.valueOf(code)
				: HttpStatus.INTERNAL_SERVER_ERROR;
		HttpStatus resolved = HttpStatus.resolve(status.value());
		String message = (status.is5xxServerError() || resolved == null) ? "Something went wrong on our side."
				: resolved.getReasonPhrase() + ".";
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("code", ErrorCodes.forStatus(status));
		error.put("message", message);
		return Map.of("error", error);
	}

}
