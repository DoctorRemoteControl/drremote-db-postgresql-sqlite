package de.drremote.db.json;

import de.drremote.db.json.api.JsonDbException;
import de.drremote.db.json.api.JsonQueries;
import de.drremote.db.json.api.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JsonQueriesDuckDbTest {
    private Connection connection;
    private JsonQueries json;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        json = JsonQueries.on(connection);
        json.update("CREATE TABLE document(id BIGINT, active BOOLEAN, amount DECIMAL(38,12), payload JSON)");
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
    }

    @Test
    void roundTripsNativeJsonAndExactNumbers() {
        BigDecimal amount = new BigDecimal("123456789012345678.123456789012");
        json.update("INSERT INTO document VALUES (?, ?, ?, ?)", 9007199254740993L, true, amount,
            JsonValue.parse("{\"tags\":[\"one\",\"two\"],\"amount\":0.12345678901234567890123456789}"));
        var row = json.queryOne("SELECT * FROM document");
        assertEquals(9007199254740993L, row.path("id").longValue());
        assertTrue(row.path("active").isBoolean());
        assertTrue(row.path("active").booleanValue());
        assertEquals(0, amount.compareTo(row.path("amount").decimalValue()));
        assertTrue(row.path("payload").isObject());
        assertEquals("two", row.path("payload").path("tags").get(1).textValue());
        assertEquals(new BigDecimal("0.12345678901234567890123456789"),
            row.path("payload").path("amount").decimalValue());
        var huge = json.queryOne("SELECT CAST(123456789012345678901234567890 AS HUGEINT) AS huge");
        assertEquals(new BigInteger("123456789012345678901234567890"), huge.path("huge").bigIntegerValue());
    }

    @Test
    void keepsJsonNullSeparateFromSqlNull() {
        json.update("INSERT INTO document(id, payload) VALUES (?, ?)", 1, JsonValue.parse("null"));
        json.update("INSERT INTO document(id, payload) VALUES (?, ?)", 2, (Object) null);
        var rows = json.query("SELECT payload, payload IS NULL AS sql_null FROM document ORDER BY id");
        assertTrue(rows.get(0).path("payload").isNull());
        assertFalse(rows.get(0).path("sql_null").booleanValue());
        assertTrue(rows.get(1).path("sql_null").booleanValue());
    }

    @Test
    void mapsDatesTimesArraysUuidAndBinaryToJsonValues() {
        var row = json.queryOne("""
            SELECT DATE '2026-11-01' AS day,
                   TIME '12:34:56.123456' AS clock,
                   TIMESTAMP '2026-11-01 12:34:56.123456' AS stamp,
                   TIMESTAMPTZ '2026-11-01 12:34:56+02:00' AS zoned,
                   [1, 2, NULL] AS numbers,
                   UUID '123e4567-e89b-12d3-a456-426614174000' AS identifier,
                   'abc'::BLOB AS binary
            """);
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
    void rejectsNonFiniteNumbersInsteadOfEmittingInvalidJson() {
        assertThrows(JsonDbException.class, () -> json.query("SELECT 'NaN'::DOUBLE AS value"));
        assertThrows(JsonDbException.class, () -> json.query("SELECT 'Infinity'::DOUBLE AS value"));
    }

    @Test
    void participatesInTheExistingTransactionWithoutCommittingIt() throws Exception {
        connection.setAutoCommit(false);
        json.update("INSERT INTO document(id, payload) VALUES (?, ?)", 1, JsonValue.parse("{\"ok\":true}"));
        assertFalse(connection.getAutoCommit());
        connection.rollback();
        assertEquals(0, json.queryOne("SELECT COUNT(*) AS count FROM document").path("count").intValue());
        assertFalse(connection.isClosed());
    }

    @Test
    void temporalArrayElementsKeepTheirPrecisionAndInstant() {
        var row = json.queryOne("""
            SELECT [TIME '12:34:56.123456'] AS clocks,
                   [TIMESTAMPTZ '2026-11-01 12:34:56.123456+02:00'] AS instants
            """);
        assertEquals("12:34:56.123456", row.path("clocks").get(0).textValue());
        assertEquals(Instant.parse("2026-11-01T10:34:56.123456Z"),
            OffsetDateTime.parse(row.path("instants").get(0).textValue()).toInstant());
    }
}
