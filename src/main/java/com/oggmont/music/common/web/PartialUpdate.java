package com.oggmont.music.common.web;

import com.oggmont.music.common.error.ApiError;
import com.oggmont.music.common.error.ApiException;
import com.oggmont.music.common.error.ErrorCodes;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The body of a {@code PATCH} request: the parsed fields plus which ones were sent.
 * <p>
 * A record can't tell an absent field from one sent as {@code null}, but a partial
 * update must: absent means "leave it", {@code null} means "clear it" (JSON Merge Patch,
 * RFC 7396). Declare the parameter as {@code @Valid @RequestBody PartialUpdate<Body>};
 * {@code Body} is a plain record whose constraints are validated as usual.
 *
 * @param <T> the body type
 * @param value the parsed body
 * @param fields JSON names of the fields that were sent
 * @param nullFields JSON names of the fields that were sent as {@code null}
 */
@JsonDeserialize(using = PartialUpdate.Deserializer.class)
public record PartialUpdate<T>(@Valid T value, Set<String> fields, Set<String> nullFields) {

	/** Whether the JSON field (snake_case, as sent) is part of the request. */
	public boolean has(String field) {
		return this.fields.contains(field);
	}

	/** Rejects {@code null} for fields that can be changed but not cleared. */
	public PartialUpdate<T> rejectNulls(String... fields) {
		List<ApiError.FieldDetail> details = Arrays.stream(fields)
			.filter(this.nullFields::contains)
			.map((field) -> new ApiError.FieldDetail(field, "can't be null"))
			.toList();
		if (!details.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.VALIDATION_FAILED,
					"'" + details.getFirst().field() + "' can't be null.", details);
		}
		return this;
	}

	/**
	 * Reads the JSON object as a tree, records its field names, then binds it to the body
	 * type taken from the declared {@code PartialUpdate<T>}.
	 */
	static final class Deserializer extends ValueDeserializer<PartialUpdate<?>> {

		private final @Nullable JavaType valueType;

		Deserializer() {
			this(null);
		}

		private Deserializer(@Nullable JavaType valueType) {
			this.valueType = valueType;
		}

		@Override
		public ValueDeserializer<?> createContextual(DeserializationContext ctxt, @Nullable BeanProperty property) {
			JavaType type = (property != null) ? property.getType() : ctxt.getContextualType();
			return (type != null) ? new Deserializer(type.containedTypeOrUnknown(0)) : this;
		}

		@Override
		public PartialUpdate<?> deserialize(JsonParser parser, DeserializationContext ctxt) {
			JsonNode tree = ctxt.readTree(parser);
			if (tree == null || !tree.isObject()) {
				return ctxt.reportInputMismatch(this, "Expected a JSON object with the fields to change");
			}
			Set<String> fields = new LinkedHashSet<>();
			Set<String> nullFields = new LinkedHashSet<>();
			for (Map.Entry<String, JsonNode> field : tree.properties()) {
				fields.add(field.getKey());
				if (field.getValue().isNull()) {
					nullFields.add(field.getKey());
				}
			}
			JavaType target = (this.valueType != null) ? this.valueType : ctxt.constructType(Object.class);
			Object value = ctxt.readTreeAsValue(tree, target);
			return new PartialUpdate<>(value, Set.copyOf(fields), Set.copyOf(nullFields));
		}

	}

}
