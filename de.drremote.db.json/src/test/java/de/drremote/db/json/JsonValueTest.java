package de.drremote.db.json;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import de.drremote.db.json.api.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JsonValueTest {
    @Test
    void snapshotsMutableDocuments() {
        var node = JsonNodeFactory.instance.objectNode().put("name", "original");
        var value = JsonValue.of(node);
        node.put("name", "changed");
        assertEquals("{\"name\":\"original\"}", value.json());
    }

    @Test
    void acceptsObjectsArraysScalarsAndJsonNull() {
        assertEquals("{\"id\":1}", JsonValue.parse(" { \"id\" : 1 } ").json());
        assertEquals("[1,true,null]", JsonValue.parse("[1,true,null]").json());
        assertEquals("\"hello\"", JsonValue.parse("\"hello\"").json());
        assertEquals("null", JsonValue.of(NullNode.instance).json());
    }

    @Test
    void preservesLargeNumbersWithoutDoubleConversion() {
        String json = "{\"id\":123456789012345678901234567890,\"amount\":0.12345678901234567890123456789}";
        assertEquals(json, JsonValue.parse(json).json());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{} {}", "{\"x\":}", "null true"})
    void rejectsIncompleteOrMultipleDocuments(String text) {
        assertThrows(IllegalArgumentException.class, () -> JsonValue.parse(text));
    }

    @Test
    void rejectsMissingNodesAndJavaNull() {
        assertThrows(IllegalArgumentException.class, () -> JsonValue.of(MissingNode.getInstance()));
        assertThrows(NullPointerException.class, () -> JsonValue.of(null));
        assertThrows(NullPointerException.class, () -> JsonValue.parse(null));
    }
}
