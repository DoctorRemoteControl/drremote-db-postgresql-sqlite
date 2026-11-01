package de.drremote.db.json.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.drremote.db.json.api.JsonDbException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.temporal.TemporalAccessor;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Internal mapping plan, validated once for a result set. */
public final class JdbcJsonMapper {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private final String[] labels;
    private final String[] typeNames;
    private final int[] types;
    private final boolean[] json;

    public JdbcJsonMapper(ResultSetMetaData metadata, Set<String> selectedJson) throws SQLException {
        int count = metadata.getColumnCount();
        labels = new String[count];
        typeNames = new String[count];
        types = new int[count];
        json = new boolean[count];
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String label = metadata.getColumnLabel(i + 1);
            if (label == null || label.isBlank()) throw new JsonDbException("Blank result column label; supply an SQL alias");
            if (!seen.add(label)) throw new JsonDbException("Duplicate result column label: " + label);
            labels[i] = label;
            typeNames[i] = Objects.toString(metadata.getColumnTypeName(i + 1), "");
            types[i] = metadata.getColumnType(i + 1);
            json[i] = selectedJson.contains(label) || "json".equalsIgnoreCase(typeNames[i])
                || "jsonb".equalsIgnoreCase(typeNames[i]);
        }
        for (String label : selectedJson) {
            if (!seen.contains(label)) throw new JsonDbException("JSON column label is missing from result: " + label);
        }
    }

    public ObjectNode read(ResultSet rows) throws SQLException {
        ObjectNode object = NODES.objectNode();
        for (int i = 0; i < labels.length; i++) object.set(labels[i], column(rows, i));
        return object;
    }

    private JsonNode column(ResultSet rows, int index) throws SQLException {
        int column = index + 1;
        if (json[index]) {
            String text = rows.getString(column);
            if (text == null) return NODES.nullNode();
            try {
                return JsonCodec.parse(text);
            } catch (JsonProcessingException | IllegalArgumentException failure) {
                throw new JsonDbException("Invalid JSON in column '" + labels[index] + "'", failure);
            }
        }
        Object value = rows.getObject(column);
        if (value == null) return NODES.nullNode();
        if (types[index] == Types.BOOLEAN || "BOOLEAN".equalsIgnoreCase(typeNames[index])
                || "BOOL".equalsIgnoreCase(typeNames[index])) {
            return NODES.booleanNode(rows.getBoolean(column));
        }
        if (types[index] == Types.TIMESTAMP_WITH_TIMEZONE) {
            return NODES.textNode(rows.getObject(column, OffsetDateTime.class).toString());
        }
        if (types[index] == Types.TIME_WITH_TIMEZONE) {
            return NODES.textNode(rows.getObject(column, OffsetTime.class).toString());
        }
        return value(value, labels[index]);
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
        if (value instanceof java.sql.Time time) return NODES.textNode(time.toLocalTime().toString());
        if (value instanceof java.sql.Timestamp timestamp) return NODES.textNode(timestamp.toLocalDateTime().toString());
        if (value instanceof TemporalAccessor || value instanceof UUID) return NODES.textNode(value.toString());
        if (value instanceof byte[] bytes) return NODES.binaryNode(bytes);
        if (value instanceof java.sql.Array array) {
            try {
                return value(array.getArray(), label);
            } finally {
                array.free();
            }
        }
        if (value.getClass().isArray()) {
            var result = NODES.arrayNode();
            for (int i = 0; i < Array.getLength(value); i++) result.add(value(Array.get(value, i), label));
            return result;
        }
        throw new JsonDbException("Unsupported JDBC value " + value.getClass().getName()
            + " in column '" + label + "'; select an explicit SQL cast");
    }
}
