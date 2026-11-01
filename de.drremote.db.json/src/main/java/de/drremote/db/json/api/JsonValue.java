package de.drremote.db.json.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import de.drremote.db.json.impl.JsonCodec;

import java.util.Objects;

/**
 * An immutable JSON parameter snapshot. Java null parameters mean SQL NULL;
 * a snapshot of the JSON literal null is a non-null JSON value.
 */
public final class JsonValue {
    private final String json;

    private JsonValue(String json) {
        this.json = json;
    }

    /**
     * Serializes a snapshot so subsequent changes cannot affect it. Non-finite
     * numbers, missing nodes, and embedded Java objects are rejected throughout
     * the tree. Jackson's nesting limit also rejects cycles/overly deep input.
     */
    public static JsonValue of(JsonNode node) {
        Objects.requireNonNull(node, "node");
        if (node.isMissingNode()) throw new IllegalArgumentException("MissingNode is not a JSON value");
        try {
            return new JsonValue(JsonCodec.serialize(node));
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Cannot serialize JSON value", failure);
        }
    }

    /** Validates exactly one complete JSON value and snapshots its serialization. */
    public static JsonValue parse(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return of(JsonCodec.parse(text));
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid JSON value", failure);
        }
    }

    /** Returns the validated, serialized JSON value. */
    public String json() {
        return json;
    }
}
