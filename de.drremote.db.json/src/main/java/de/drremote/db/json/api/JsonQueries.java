package de.drremote.db.json.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.drremote.db.json.impl.JdbcJsonMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * JSON query results and parameters on a caller-owned JDBC connection.
 * Use inside an existing transaction callback. This helper never closes,
 * commits, rolls back, or changes settings on its connection. It is confined
 * to the same thread/lifetime as that connection.
 */
public final class JsonQueries {
    private final Connection connection;
    private final Set<String> jsonColumns;

    private JsonQueries(Connection connection, Set<String> jsonColumns) {
        this.connection = connection;
        this.jsonColumns = jsonColumns;
    }

    /** Creates an adapter without taking ownership of the connection. */
    public static JsonQueries on(Connection connection) {
        return new JsonQueries(Objects.requireNonNull(connection, "connection"), Set.of());
    }

    /**
     * Returns a new helper that additionally parses these exact result labels
     * as JSON. Native JSON/JSONB columns are recognized without configuration.
     */
    public JsonQueries withJsonColumns(String... labels) {
        Objects.requireNonNull(labels, "labels");
        Set<String> selected = new HashSet<>(jsonColumns);
        for (String label : labels) {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("JSON column label must not be blank");
            selected.add(label);
        }
        return new JsonQueries(connection, Set.copyOf(selected));
    }

    /** Returns one JSON object per row, using JDBC column labels as keys. */
    public ArrayNode query(String sql, Object... parameters) {
        try (PreparedStatement statement = prepare(sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            JdbcJsonMapper mapper = new JdbcJsonMapper(rows.getMetaData(), jsonColumns);
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            while (rows.next()) result.add(mapper.read(rows));
            return result;
        } catch (SQLException failure) {
            throw new JsonDbException("Failed to execute JSON query", failure);
        }
    }

    /** Returns null for no row and rejects a result with more than one row. */
    public ObjectNode queryOne(String sql, Object... parameters) {
        try (PreparedStatement statement = prepare(sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            JdbcJsonMapper mapper = new JdbcJsonMapper(rows.getMetaData(), jsonColumns);
            if (!rows.next()) return null;
            ObjectNode result = mapper.read(rows);
            if (rows.next()) throw new JsonDbException("Expected at most one JSON row");
            return result;
        } catch (SQLException failure) {
            throw new JsonDbException("Failed to execute single-row JSON query", failure);
        }
    }

    /** Executes a prepared update and returns its JDBC affected-row count. */
    public int update(String sql, Object... parameters) {
        try (PreparedStatement statement = prepare(sql, parameters)) {
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new JsonDbException("Failed to execute JSON update", failure);
        }
    }

    private PreparedStatement prepare(String sql, Object[] parameters) throws SQLException {
        Objects.requireNonNull(sql, "sql");
        if (sql.isBlank()) throw new IllegalArgumentException("SQL must not be blank");
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            Boolean postgresql = null;
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    Object value = parameters[i];
                    if (value instanceof JsonNode node) value = JsonValue.of(node);
                    if (value instanceof JsonValue json) {
                        if (postgresql == null) {
                            postgresql = "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
                        }
                        if (postgresql) statement.setObject(i + 1, json.json(), Types.OTHER);
                        else statement.setString(i + 1, json.json());
                    } else {
                        statement.setObject(i + 1, value);
                    }
                }
            }
            return statement;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                statement.close();
            } catch (SQLException closeFailure) {
                if (closeFailure != failure) failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }
}
