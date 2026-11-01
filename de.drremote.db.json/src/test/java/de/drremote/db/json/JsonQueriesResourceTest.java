package de.drremote.db.json;

import de.drremote.db.json.api.JsonDbException;
import de.drremote.db.json.api.JsonQueries;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JsonQueriesResourceTest {
    @Test
    void closesPreparedStatementIfBindingFails() {
        var closed = new AtomicBoolean();
        var failure = new SQLException("binding failed");
        var statement = proxy(PreparedStatement.class, (p, method, args) -> {
            if (method.getName().equals("setObject")) throw failure;
            if (method.getName().equals("close")) { closed.set(true); return null; }
            throw new AssertionError(method.getName());
        });
        var error = assertThrows(JsonDbException.class,
            () -> JsonQueries.on(connection(statement)).query("SELECT ?", "value"));
        assertSame(failure, error.getCause());
        assertTrue(closed.get());
    }

    @Test
    void preservesBindingFailureWhenCleanupAlsoFails() {
        var failure = new SQLException("binding failed");
        var cleanup = new SQLException("cleanup failed");
        var statement = proxy(PreparedStatement.class, (p, method, args) -> {
            if (method.getName().equals("setObject")) throw failure;
            if (method.getName().equals("close")) throw cleanup;
            throw new AssertionError(method.getName());
        });
        var error = assertThrows(JsonDbException.class,
            () -> JsonQueries.on(connection(statement)).update("UPDATE item SET value = ?", "value"));
        assertSame(failure, error.getCause());
        assertArrayEquals(new Throwable[]{cleanup}, failure.getSuppressed());
    }

    @Test
    void closesPreparedStatementIfExecutionFails() {
        var closed = new AtomicBoolean();
        var failure = new SQLException("execution failed");
        var statement = proxy(PreparedStatement.class, (p, method, args) -> {
            if (method.getName().equals("executeQuery")) throw failure;
            if (method.getName().equals("close")) { closed.set(true); return null; }
            throw new AssertionError(method.getName());
        });
        var error = assertThrows(JsonDbException.class,
            () -> JsonQueries.on(connection(statement)).query("SELECT 1"));
        assertSame(failure, error.getCause());
        assertTrue(closed.get());
    }

    @Test
    void closesResultsAndStatementIfColumnMappingFails() {
        var resultClosed = new AtomicBoolean();
        var statementClosed = new AtomicBoolean();
        var metadata = proxy(ResultSetMetaData.class, (p, method, args) -> switch (method.getName()) {
            case "getColumnCount" -> 2;
            case "getColumnLabel" -> "duplicate";
            case "getColumnTypeName" -> "INTEGER";
            case "getColumnType" -> Types.INTEGER;
            default -> throw new AssertionError(method.getName());
        });
        var result = proxy(ResultSet.class, (p, method, args) -> {
            if (method.getName().equals("getMetaData")) return metadata;
            if (method.getName().equals("close")) { resultClosed.set(true); return null; }
            throw new AssertionError(method.getName());
        });
        var statement = proxy(PreparedStatement.class, (p, method, args) -> {
            if (method.getName().equals("executeQuery")) return result;
            if (method.getName().equals("close")) { statementClosed.set(true); return null; }
            throw new AssertionError(method.getName());
        });
        assertThrows(JsonDbException.class, () -> JsonQueries.on(connection(statement)).query("SELECT a, b"));
        assertTrue(resultClosed.get());
        assertTrue(statementClosed.get());
    }

    private static Connection connection(PreparedStatement statement) {
        return proxy(Connection.class, (p, method, args) -> {
            if (method.getName().equals("prepareStatement")) return statement;
            throw new AssertionError("Caller-owned connection operation: " + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
