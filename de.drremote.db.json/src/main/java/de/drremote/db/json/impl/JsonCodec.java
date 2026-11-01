package de.drremote.db.json.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Internal shared codec, configured once and never exposed for mutation. */
public final class JsonCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private JsonCodec() {}

    public static JsonNode parse(String text) throws JsonProcessingException {
        JsonNode node = MAPPER.readTree(text);
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException("Expected a complete JSON value");
        }
        return node;
    }

    public static String serialize(JsonNode node) throws JsonProcessingException {
        validate(node);
        return MAPPER.writeValueAsString(node);
    }

    private static void validate(JsonNode node) {
        // Walk tokens without recursive Java calls; the depth limit also bounds cycles.
        int depth = 0;
        int maxDepth = MAPPER.getFactory().streamWriteConstraints().getMaxNestingDepth();
        try (JsonParser parser = node.traverse()) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    if (++depth > maxDepth) throw new IllegalArgumentException("JSON node nesting exceeds " + maxDepth);
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                } else if (token == JsonToken.NOT_AVAILABLE) {
                    throw new IllegalArgumentException("MissingNode is not a JSON value");
                } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                    JsonParser.NumberType type = parser.getNumberType();
                    if ((type == JsonParser.NumberType.DOUBLE || type == JsonParser.NumberType.FLOAT)
                            && !Double.isFinite(parser.getDoubleValue())) {
                        throw new IllegalArgumentException("Non-finite numbers are not JSON values");
                    }
                } else if (token == JsonToken.VALUE_EMBEDDED_OBJECT
                        && !(parser.getEmbeddedObject() instanceof byte[])) {
                    throw new IllegalArgumentException("Embedded Java objects are not JSON values; convert them to a JSON tree first");
                }
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot validate JSON node", failure);
        }
    }
}
