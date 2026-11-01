package de.drremote.db.json;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import de.drremote.db.json.api.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

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

    @ParameterizedTest
    @MethodSource("invalidNodeTrees")
    void rejectsInvalidValuesThroughoutTheNodeTree(JsonNode node) {
        assertThrows(IllegalArgumentException.class, () -> JsonValue.of(node));
    }

    static Stream<JsonNode> invalidNodeTrees() {
        var factory = JsonNodeFactory.instance;
        return Stream.of(
            DoubleNode.valueOf(Double.NaN),
            FloatNode.valueOf(Float.POSITIVE_INFINITY),
            factory.objectNode().put("amount", Double.NEGATIVE_INFINITY),
            factory.arrayNode().add(Double.NaN),
            factory.objectNode().set("value", MissingNode.getInstance()),
            factory.arrayNode().add(MissingNode.getInstance()),
            factory.pojoNode(new Object()));
    }

    @Test
    void acceptsHugeFiniteDecimalsAndSharedValidSubtrees() {
        assertEquals("1E+1000", JsonValue.of(JsonNodeFactory.instance.numberNode(new BigDecimal("1E+1000"))).json());
        var child = JsonNodeFactory.instance.objectNode().put("ok", true);
        var root = JsonNodeFactory.instance.objectNode();
        root.set("first", child);
        root.set("second", child);
        assertEquals("{\"first\":{\"ok\":true},\"second\":{\"ok\":true}}", JsonValue.of(root).json());
    }

    @Test
    void rejectsCyclicNodes() {
        var root = JsonNodeFactory.instance.objectNode();
        root.set("self", root);
        assertThrows(IllegalArgumentException.class, () -> JsonValue.of(root));
    }
}
