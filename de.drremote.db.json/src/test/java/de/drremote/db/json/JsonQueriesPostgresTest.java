package de.drremote.db.json;

import de.drremote.db.json.api.JsonDbException;
import de.drremote.db.json.api.JsonQueries;
import de.drremote.db.json.api.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DRREMOTE_TEST_POSTGRES_URL", matches = ".+")
class JsonQueriesPostgresTest {
    private Connection connection;
    private JsonQueries json;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(System.getenv("DRREMOTE_TEST_POSTGRES_URL"),
            System.getenv().getOrDefault("DRREMOTE_TEST_POSTGRES_USER", "postgres"),
            System.getenv().getOrDefault("DRREMOTE_TEST_POSTGRES_PASSWORD", "postgres"));
        json = JsonQueries.on(connection);
        json.update("CREATE TEMP TABLE document(id BIGINT, active BOOLEAN, amount NUMERIC(38,12), payload JSONB, raw JSON)");
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void bindsAndReadsNativeJsonAndJsonbWithoutDriverObjectsOrDoubleEncoding() {
        var payload = JsonValue.parse("{\"tags\":[\"one\",\"two\"],\"enabled\":true}");
        BigDecimal amount = new BigDecimal("123456789012345678.123456789012");
        json.update("INSERT INTO document VALUES (?, ?, ?, ?, ?)", 9007199254740993L, true, amount, payload, payload);
        var row = json.queryOne("SELECT id, active, amount, payload, raw, payload->'tags' AS tags, payload::text AS text FROM document");
        assertEquals(9007199254740993L, row.path("id").longValue());
        assertTrue(row.path("active").isBoolean());
        assertEquals(0, amount.compareTo(row.path("amount").decimalValue()));
        assertTrue(row.path("payload").isObject());
        assertTrue(row.path("raw").isObject());
        assertTrue(row.path("tags").isArray());
        assertEquals("two", row.path("tags").get(1).textValue());
        assertTrue(row.path("text").isTextual());
        assertTrue(json.withJsonColumns("text").queryOne("SELECT payload::text AS text FROM document").path("text").isObject());
    }

    @Test
    void distinguishesJsonNullFromSqlNull() {
        json.update("INSERT INTO document(id, payload) VALUES (?, ?)", 1, JsonValue.parse("null"));
        json.update("INSERT INTO document(id, payload) VALUES (?, ?)", 2, (Object) null);
        var rows = json.query("SELECT payload, payload IS NULL AS sql_null FROM document ORDER BY id");
        assertTrue(rows.get(0).path("payload").isNull());
        assertFalse(rows.get(0).path("sql_null").booleanValue());
        assertTrue(rows.get(1).path("sql_null").booleanValue());
    }

    @Test
    void preservesBigDecimalsDatesTimesArraysUuidAndBinary() {
        var row = json.queryOne("""
            SELECT 123456789012345678901234567890::NUMERIC AS huge,
                   DATE '2026-11-01' AS day,
                   TIME '12:34:56.123456' AS clock,
                   TIMESTAMP '2026-11-01 12:34:56.123456' AS stamp,
                   TIMESTAMPTZ '2026-11-01 12:34:56+02:00' AS zoned,
                   ARRAY[1, 2, NULL] AS numbers,
                   UUID '123e4567-e89b-12d3-a456-426614174000' AS identifier,
                   'abc'::BYTEA AS binary
            """);
        assertEquals(0, new BigDecimal("123456789012345678901234567890").compareTo(row.path("huge").decimalValue()));
        assertEquals("2026-11-01", row.path("day").textValue());
        assertEquals("12:34:56.123456", row.path("clock").textValue());
        assertEquals("2026-11-01T12:34:56.123456", row.path("stamp").textValue());
        assertEquals(Instant.parse("2026-11-01T10:34:56Z"), OffsetDateTime.parse(row.path("zoned").textValue()).toInstant());
        assertEquals(2, row.path("numbers").get(1).intValue());
        assertTrue(row.path("numbers").get(2).isNull());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", row.path("identifier").textValue());
        assertTrue(row.path("binary").isTextual());
        assertEquals("YWJj", row.path("binary").textValue());
    }

    @Test
    void rejectsNonFiniteNumbers() {
        assertThrows(JsonDbException.class, () -> json.query("SELECT 'NaN'::DOUBLE PRECISION AS value"));
        assertThrows(JsonDbException.class, () -> json.query("SELECT 'Infinity'::DOUBLE PRECISION AS value"));
    }

    @Test
    void leavesRollbackAndConnectionOwnershipWithTheExistingTransaction() throws Exception {
        connection.setAutoCommit(false);
        json.update("INSERT INTO document(id, payload) VALUES (?, ?)", 1, JsonValue.parse("[1,true,null]"));
        assertFalse(connection.getAutoCommit());
        connection.rollback();
        assertEquals(0, json.queryOne("SELECT COUNT(*) AS count FROM document").path("count").intValue());
        assertFalse(connection.isClosed());
    }
}
