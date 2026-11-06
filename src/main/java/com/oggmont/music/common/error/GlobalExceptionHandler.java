package com.oggmont.music.common.error;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;

/**
 * Renders every error raised while handling a request as an {@link ApiError}. Spring
 * MVC's own exceptions (404, 405, 415...) are handled by the superclass and converted in
 * {@link #handleExceptionInternal}, so the format is the same everywhere.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String SERVER_ERROR_MESSAGE = "Something went wrong on our side. Please try again.";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.status()).body(ex.toApiError());
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiError> handleDataAccessException(DataAccessException ex) {
		Optional<ApiException> translated = DatabaseErrors.translate(ex);
		if (translated.isPresent()) {
			log.debug("Database rejected the request: {}", ex.getMostSpecificCause().getMessage());
			return handleApiException(translated.get());
		}
		if (ex instanceof DataAccessResourceFailureException || ex instanceof TransientDataAccessException
				|| ex instanceof QueryTimeoutException) {
			log.error("Database unavailable", ex);
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(ApiError.of(ErrorCodes.SERVICE_UNAVAILABLE, "The database is unavailable. Please try again shortly."));
		}
		log.error("Database error", ex);
		return ResponseEntity.internalServerError().body(ApiError.of(ErrorCodes.INTERNAL_ERROR, SERVER_ERROR_MESSAGE));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
		List<ApiError.FieldDetail> details = ex.getConstraintViolations()
			.stream()
			.map(v -> new ApiError.FieldDetail(toSnakeCase(lastNode(v.getPropertyPath().toString())), v.getMessage()))
			.toList();
		return ResponseEntity.unprocessableContent().body(validationError(details));
	}

	/**
	 * Lets Spring Security's {@code ExceptionTranslationFilter} answer: it knows whether the
	 * caller is anonymous (401) or lacks a role (403).
	 */
	@ExceptionHandler({ AccessDeniedException.class, AuthenticationException.class })
	void rethrowSecurityException(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.internalServerError().body(ApiError.of(ErrorCodes.INTERNAL_ERROR, SERVER_ERROR_MESSAGE));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return validationFailed(fieldDetails(ex.getBindingResult()), headers, ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ApiError.FieldDetail> details = new ArrayList<>();
		ex.getParameterValidationResults().forEach(result -> {
			String parameter = parameterName(result.getMethodParameter());
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				if (error instanceof FieldError fieldError) {
					details.add(new ApiError.FieldDetail(fieldName(fieldError.getField()), message(fieldError)));
				}
				else {
					details.add(new ApiError.FieldDetail(parameter, String.valueOf(error.getDefaultMessage())));
				}
			}
		});
		return validationFailed(details, headers, ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String message = "The request body is missing or isn't valid JSON.";
		Throwable cause = ex.getMostSpecificCause();
		DatabindException databind = findCause(ex, DatabindException.class);
		if (databind != null && !databind.getPath().isEmpty()) {
			message = "Invalid value for '" + jsonPath(databind.getPath()) + "'.";
		}
		else if (cause instanceof JacksonException jackson && jackson.getLocation() != null) {
			message = "Malformed JSON at line " + jackson.getLocation().getLineNr() + ", column "
					+ jackson.getLocation().getColumnNr() + ".";
		}
		return error(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, message, headers, ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = (ex instanceof MethodArgumentTypeMismatchException mismatch) ? parameterName(mismatch.getParameter())
				: String.valueOf(ex.getPropertyName());
		String value = abbreviate(String.valueOf(ex.getValue()));
		return error(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "Invalid value '" + value + "' for '" + name + "'.",
				headers, ex, request);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String path = ex.getResourcePath().startsWith("/") ? ex.getResourcePath() : "/" + ex.getResourcePath();
		return error(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "No endpoint " + ex.getHttpMethod() + " " + path + ".",
				headers, ex, request);
	}

	/** Every other Spring MVC exception ends here: convert its ProblemDetail to an ApiError. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ApiError) {
			return super.handleExceptionInternal(ex, body, headers, statusCode, request);
		}
		if (body == null && ex instanceof ErrorResponse errorResponse) {
			body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
		}
		String message;
		if (ex instanceof InvalidApiVersionException) {
			message = "Unsupported API version. This API serves version 1: /api/v1/...";
		}
		else if (statusCode.is5xxServerError()) {
			log.error("Request failed", ex);
			message = SERVER_ERROR_MESSAGE;
		}
		else if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
			message = problem.getDetail();
		}
		else {
			HttpStatus resolved = HttpStatus.resolve(statusCode.value());
			message = (resolved != null) ? resolved.getReasonPhrase() + "." : "Request failed.";
		}
		return super.handleExceptionInternal(ex, ApiError.of(ErrorCodes.forStatus(statusCode), message), headers,
				statusCode, request);
	}

	private ResponseEntity<Object> validationFailed(List<ApiError.FieldDetail> details, HttpHeaders headers, Exception ex,
			WebRequest request) {
		return super.handleExceptionInternal(ex, validationError(details), headers, HttpStatus.UNPROCESSABLE_CONTENT,
				request);
	}

	private ResponseEntity<Object> error(HttpStatus status, String code, String message, HttpHeaders headers,
			Exception ex, WebRequest request) {
		return super.handleExceptionInternal(ex, ApiError.of(code, message), headers, status, request);
	}

	private static ApiError validationError(List<ApiError.FieldDetail> details) {
		String message = switch (details.size()) {
			case 0 -> "The request is invalid.";
			case 1 -> "'" + details.getFirst().field() + "' " + details.getFirst().message() + ".";
			default -> "The request has " + details.size() + " invalid fields.";
		};
		return ApiError.of(ErrorCodes.VALIDATION_FAILED, message, details);
	}

	private static List<ApiError.FieldDetail> fieldDetails(BindingResult result) {
		List<ApiError.FieldDetail> details = new ArrayList<>();
		result.getFieldErrors()
			.forEach(error -> details.add(new ApiError.FieldDetail(fieldName(error.getField()), message(error))));
		result.getGlobalErrors()
			.forEach(error -> details.add(new ApiError.FieldDetail(error.getObjectName(),
					String.valueOf(error.getDefaultMessage()))));
		return details;
	}

	private static String message(FieldError error) {
		return error.isBindingFailure() ? "has an invalid value" : String.valueOf(error.getDefaultMessage());
	}

	/** The name the client used: the {@code @RequestParam}/{@code @PathVariable}/{@code @RequestHeader} name. */
	private static String parameterName(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null && !requestParam.name().isEmpty()) {
			return requestParam.name();
		}
		PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null && !pathVariable.name().isEmpty()) {
			return pathVariable.name();
		}
		RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
		if (requestHeader != null && !requestHeader.name().isEmpty()) {
			return requestHeader.name();
		}
		String name = parameter.getParameterName();
		return (name != null) ? toSnakeCase(name) : "parameter";
	}

	private static <T extends Throwable> @Nullable T findCause(Throwable ex, Class<T> type) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (type.isInstance(t)) {
				return type.cast(t);
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return null;
	}

	private static String jsonPath(List<JacksonException.Reference> path) {
		StringBuilder result = new StringBuilder();
		for (JacksonException.Reference reference : path) {
			if (reference.getPropertyName() != null) {
				if (!result.isEmpty()) {
					result.append('.');
				}
				result.append(reference.getPropertyName());
			}
			else if (reference.getIndex() >= 0) {
				result.append('[').append(reference.getIndex()).append(']');
			}
		}
		return result.toString();
	}

	/**
	 * The field as the client named it: snake_case, without the {@code value.} prefix that
	 * {@code PartialUpdate} adds to the path.
	 */
	static String fieldName(String path) {
		String field = path.startsWith("value.") ? path.substring("value.".length()) : path;
		return toSnakeCase(field);
	}

	/** {@code trackIds[0]} -> {@code track_ids[0]}, {@code genres[1].genreId} -> {@code genres[1].genre_id}. */
	static String toSnakeCase(String path) {
		StringBuilder result = new StringBuilder(path.length() + 8);
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (Character.isUpperCase(c)) {
				if (i > 0 && path.charAt(i - 1) != '.' && path.charAt(i - 1) != '[') {
					result.append('_');
				}
				result.append(Character.toLowerCase(c));
			}
			else {
				result.append(c);
			}
		}
		return result.toString();
	}

	private static String lastNode(String propertyPath) {
		int dot = propertyPath.lastIndexOf('.');
		return (dot >= 0) ? propertyPath.substring(dot + 1) : propertyPath;
	}

	private static String abbreviate(String value) {
		return (value.length() > 60) ? value.substring(0, 57) + "..." : value;
	}

}
