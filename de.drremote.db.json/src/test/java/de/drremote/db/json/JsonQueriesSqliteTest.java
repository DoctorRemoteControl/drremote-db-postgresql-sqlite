package de.drremote.db.json;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import de.drremote.db.json.api.JsonDbException;
import de.drremote.db.json.api.JsonQueries;
import de.drremote.db.json.api.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class JsonQueriesSqliteTest {
    private Connection connection;
    private JsonQueries json;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE item(id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN, payload TEXT, optional TEXT)");
        }
        json = JsonQueries.on(connection);
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
    }

    @Test
    void mapsAliasesPrimitivesAndSqlNullWithoutPrecisionLoss() {
        json.update("INSERT INTO item VALUES (?, ?, ?, ?, ?)", 9007199254740993L, "Ada", true, "true", null);
        var rows = json.query("SELECT id AS customer_id, name, active, optional FROM item");
        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertTrue(row.get("customer_id").isIntegralNumber());
        assertEquals(9007199254740993L, row.get("customer_id").longValue());
        assertEquals("Ada", row.get("name").textValue());
        assertTrue(row.get("active").isBoolean());
        assertTrue(row.get("active").booleanValue());
        assertTrue(row.has("optional"));
        assertTrue(row.get("optional").isNull());
    }

    @Test
    void handlesEmptySingleAndMultipleResults() {
        assertTrue(json.query("SELECT * FROM item").isEmpty());
        assertNull(json.queryOne("SELECT * FROM item"));
        assertEquals(1, json.queryOne("SELECT 1 AS id").get("id").intValue());
        assertThrows(JsonDbException.class, () -> json.queryOne("SELECT 1 AS id UNION ALL SELECT 2"));
    }

    @Test
    void parsesOnlyExplicitJsonTextColumnsAndDoesNotMutateTheOriginalHelper() {
        json.update("INSERT INTO item(id, payload, name) VALUES (?, ?, ?)", 1,
            JsonValue.parse("{\"tags\":[\"one\",\"two\"],\"enabled\":false}"), "{\"ordinary\":true}");
        var parsed = json.withJsonColumns("payload").queryOne("SELECT payload, name FROM item");
        assertTrue(parsed.get("payload").isObject());
        assertEquals("two", parsed.path("payload").path("tags").get(1).textValue());
        assertTrue(parsed.get("name").isTextual());
        assertTrue(json.queryOne("SELECT payload FROM item").get("payload").isTextual());
    }

    @Test
    void bindsDirectJsonNodesAndDistinguishesJsonNullFromSqlNull() {
        var node = JsonNodeFactory.instance.objectNode().put("id", 7);
        json.update("INSERT INTO item(id, payload) VALUES (?, ?)", 1, node);
        json.update("INSERT INTO item(id, payload) VALUES (?, ?)", 2, NullNode.instance);
        json.update("INSERT INTO item(id, payload) VALUES (?, ?)", 3, (Object) null);
        assertEquals(7, json.withJsonColumns("payload").queryOne(
            "SELECT payload FROM item WHERE id = ?", 1).path("payload").path("id").intValue());
        assertEquals("null", json.queryOne("SELECT payload FROM item WHERE id = 2").path("payload").textValue());
        assertTrue(json.queryOne("SELECT payload FROM item WHERE id = 3").path("payload").isNull());
    }

    @Test
    void keepsUnusualStringsAsPreparedStatementValues() {
        String name = "O'Reilly; DROP TABLE item; -- \u2603";
        json.update("INSERT INTO item(id, name) VALUES (?, ?)", 1, name);
        assertEquals(name, json.queryOne("SELECT name FROM item WHERE id = ?", 1).path("name").textValue());
        assertEquals(1, json.queryOne("SELECT COUNT(*) AS count FROM item").path("count").intValue());
    }

    @Test
    void rejectsAmbiguousOrMissingLabelsEvenOnEmptyResults() {
        assertThrows(JsonDbException.class, () -> json.query("SELECT 1 AS id, 2 AS id"));
        assertThrows(JsonDbException.class, () -> json.query("SELECT 1 AS \"\""));
        assertThrows(JsonDbException.class, () -> json.withJsonColumns("missing").query("SELECT id FROM item"));
    }

    @Test
    void malformedStoredJsonHasContextAndDoesNotCloseTheConnection() throws Exception {
        json.update("INSERT INTO item(id, payload) VALUES (1, ?)", "{invalid}");
        var failure = assertThrows(JsonDbException.class,
            () -> json.withJsonColumns("payload").query("SELECT payload FROM item"));
        assertTrue(failure.getMessage().contains("payload"));
        assertNotNull(failure.getCause());
        assertFalse(connection.isClosed());
        assertEquals(1, json.queryOne("SELECT COUNT(*) AS count FROM item").path("count").intValue());
    }

    @Test
    void leavesTransactionOwnershipWithTheCaller() throws Exception {
        connection.setAutoCommit(false);
        json.update("INSERT INTO item(id, name) VALUES (?, ?)", 1, "rollback");
        assertFalse(connection.getAutoCommit());
        assertFalse(connection.isClosed());
        connection.rollback();
        assertEquals(0, json.queryOne("SELECT COUNT(*) AS count FROM item").path("count").intValue());
        assertFalse(connection.getAutoCommit());
    }

    @Test
    void heterogeneousColumnValuesKeepTheirTypesRegardlessOfFirstRow() {
        var binaryFirst = json.query("SELECT x'61' AS v UNION ALL SELECT 'hello' UNION ALL SELECT 123");
        assertEquals("YQ==", binaryFirst.get(0).path("v").textValue());
        assertEquals("hello", binaryFirst.get(1).path("v").textValue());
        assertTrue(binaryFirst.get(2).path("v").isIntegralNumber());
        assertEquals(123, binaryFirst.get(2).path("v").intValue());
        var textFirst = json.query("SELECT 'hello' AS v UNION ALL SELECT x'61' UNION ALL SELECT 123");
        assertEquals("hello", textFirst.get(0).path("v").textValue());
        assertEquals("YQ==", textFirst.get(1).path("v").textValue());
        assertEquals(123, textFirst.get(2).path("v").intValue());
    }

    @Test
    void invalidDirectJsonNodesAreRejectedBeforeAnyDataIsWritten() throws Exception {
        var document = JsonNodeFactory.instance.objectNode().put("amount", Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,
            () -> json.update("INSERT INTO item(id, payload) VALUES (?, ?)", 1, document));
        assertFalse(connection.isClosed());
        assertEquals(0, json.queryOne("SELECT COUNT(*) AS count FROM item").path("count").intValue());
    }

    @Test
    void booleanDeclarationsDoNotCoerceUnrelatedSqliteValues() {
        json.update("CREATE TABLE mixed_flag(value BOOLEAN)");
        json.update("INSERT INTO mixed_flag VALUES (?), (?), (?)", true, "hello", 2);
        var rows = json.query("SELECT value FROM mixed_flag ORDER BY rowid");
        assertTrue(rows.get(0).path("value").booleanValue());
        assertEquals("hello", rows.get(1).path("value").textValue());
        assertTrue(rows.get(2).path("value").isIntegralNumber());
        assertEquals(2, rows.get(2).path("value").intValue());
    }
}
