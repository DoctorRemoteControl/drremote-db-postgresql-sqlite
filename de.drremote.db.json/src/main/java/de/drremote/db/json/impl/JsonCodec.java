package de.drremote.db.json.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        return MAPPER.writeValueAsString(node);
    }
}
