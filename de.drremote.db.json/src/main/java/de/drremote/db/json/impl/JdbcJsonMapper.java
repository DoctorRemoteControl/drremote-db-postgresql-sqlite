package de.drremote.db.json.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.drremote.db.json.api.JsonDbException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.time.OffsetTime;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Internal mapping plan, validated once for a result set. */
public final class JdbcJsonMapper {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private final Column[] columns;

    private record Column(String label, String typeName, int type, boolean json) {}

    @FunctionalInterface
    private interface SqlResource extends AutoCloseable {
        @Override void close() throws SQLException;
    }

    public JdbcJsonMapper(ResultSetMetaData metadata, Set<String> selectedJson) throws SQLException {
        columns = new Column[metadata.getColumnCount()];
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < columns.length; i++) {
            String label = metadata.getColumnLabel(i + 1);
            if (label == null || label.isBlank()) throw new JsonDbException("Blank result column label; supply an SQL alias");
            if (!seen.add(label)) throw new JsonDbException("Duplicate result column label: " + label);
            columns[i] = metadata(metadata, i + 1, label, selectedJson.contains(label));
        }
        for (String label : selectedJson) {
            if (!seen.contains(label)) throw new JsonDbException("JSON column label is missing from result: " + label);
        }
    }

    private Column metadata(ResultSetMetaData metadata, int index, String label, boolean selected) throws SQLException {
        String name = Objects.toString(metadata.getColumnTypeName(index), "");
        return new Column(label, name, metadata.getColumnType(index), selected
            || "json".equalsIgnoreCase(name) || "jsonb".equalsIgnoreCase(name));
    }

    public ObjectNode read(ResultSet rows) throws SQLException {
        ObjectNode object = NODES.objectNode();
        for (int i = 0; i < columns.length; i++) object.set(columns[i].label(), column(rows, i + 1, columns[i]));
        return object;
    }

    private JsonNode column(ResultSet rows, int index, Column column) throws SQLException {
        if (column.json()) {
            String text = rows.getString(index);
            if (text == null) return NODES.nullNode();
            try {
                return JsonCodec.parse(text);
            } catch (JsonProcessingException | IllegalArgumentException failure) {
                throw new JsonDbException("Invalid JSON in column '" + column.label() + "'", failure);
            }
        }
        // SQLite result metadata can describe the first row rather than every value.
        Object value = rows.getObject(index);
        if (value == null) return NODES.nullNode();
        boolean bool = column.type() == Types.BOOLEAN || "BOOLEAN".equalsIgnoreCase(column.typeName())
            || "BOOL".equalsIgnoreCase(column.typeName());
        if (bool && (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            long number = ((Number) value).longValue();
            if (number == 0 || number == 1) return NODES.booleanNode(number == 1);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            boolean zoned = column.type() == Types.TIMESTAMP_WITH_TIMEZONE
                || "timestamptz".equalsIgnoreCase(column.typeName())
                || "TIMESTAMP WITH TIME ZONE".equalsIgnoreCase(column.typeName());
            return NODES.textNode(zoned ? timestamp.toInstant().toString() : timestamp.toLocalDateTime().toString());
        }
        if (column.type() == Types.TIME_WITH_TIMEZONE || value instanceof java.sql.Time
                && ("timetz".equalsIgnoreCase(column.typeName()) || "TIME WITH TIME ZONE".equalsIgnoreCase(column.typeName()))) {
            return NODES.textNode(rows.getObject(index, OffsetTime.class).toString());
        }
        if (value instanceof java.sql.Time) {
            // java.sql.Time drops fractional seconds; the JDBC text retains them.
            return NODES.textNode(rows.getString(index));
        }
        return value(value, column.label());
    }

    private JsonNode sqlArray(java.sql.Array array, String label) throws SQLException {
        try (SqlResource free = array::free; ResultSet elements = array.getResultSet()) {
            Column element;
            try {
                element = metadata(elements.getMetaData(), 2, label + "[]", false);
            } catch (SQLFeatureNotSupportedException unsupported) {
                // DuckDB exposes array element types on Array rather than its ResultSet.
                String name = Objects.toString(array.getBaseTypeName(), "");
                element = new Column(label + "[]", name, array.getBaseType(),
                    "json".equalsIgnoreCase(name) || "jsonb".equalsIgnoreCase(name));
            }
            var result = NODES.arrayNode();
            while (elements.next()) result.add(column(elements, 2, element));
            return result;
        }
    }

    private JsonNode binary(Blob blob, String label) throws SQLException {
        try (SqlResource free = blob::free) {
            long length = blob.length();
            if (length > Integer.MAX_VALUE) throw new JsonDbException("Binary value too large to materialize in column '" + label + "'");
            return NODES.textNode(Base64.getEncoder().encodeToString(blob.getBytes(1, (int) length)));
        }
    }

    private JsonNode value(Object value, String label) throws SQLException {
        if (value == null) return NODES.nullNode();
        if (value instanceof String text) return NODES.textNode(text);
        if (value instanceof Boolean bool) return NODES.booleanNode(bool);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return NODES.numberNode(((Number) value).intValue());
        if (value instanceof Long number) return NODES.numberNode(number);
        if (value instanceof BigInteger number) return NODES.numberNode(number);
        if (value instanceof BigDecimal number) return NODES.numberNode(number);
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) throw new JsonDbException("Non-finite number in column '" + label + "'");
            return NODES.numberNode(number);
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) throw new JsonDbException("Non-finite number in column '" + label + "'");
            return NODES.numberNode(number);
        }
        if (value instanceof java.sql.Date date) return NODES.textNode(date.toLocalDate().toString());
        if (value instanceof TemporalAccessor || value instanceof UUID) return NODES.textNode(value.toString());
        if (value instanceof byte[] bytes) return NODES.textNode(Base64.getEncoder().encodeToString(bytes));
        if (value instanceof Blob blob) return binary(blob, label);
        if (value instanceof java.sql.Array array) return sqlArray(array, label);
        if (value.getClass().isArray()) {
            var result = NODES.arrayNode();
            for (int i = 0; i < Array.getLength(value); i++) result.add(value(Array.get(value, i), label));
            return result;
        }
        throw new JsonDbException("Unsupported JDBC value " + value.getClass().getName()
            + " in column '" + label + "'; select an explicit SQL cast");
    }
}
