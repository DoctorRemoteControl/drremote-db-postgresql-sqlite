package de.drremote.db.duckdb.impl.jdbc;

import de.drremote.db.duckdb.api.DbCoreException;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JdbcQueryRunnerTest {
    @Test void singleRowQueryRejectsSecondRowWithoutMappingTheWholeResult() {
        Jdbc fixture = new Jdbc(100);
        AtomicInteger mappings = new AtomicInteger();
        assertThrows(DbCoreException.class, () -> new JdbcQueryRunner(fixture.connection)
            .queryOne("SELECT value FROM items", row -> mappings.incrementAndGet()));
        assertEquals(1, mappings.get(), "Only the first row should be mapped");
        assertEquals(2, fixture.nextCalls, "A second row is enough to detect ambiguity");
        fixture.assertClosed(true);
    }

    @Test void singleRowQueryPreservesEmptyAndSingleRowResults() {
        for (int count : new int[]{0, 1}) {
            Jdbc fixture = new Jdbc(count);
            Integer value = new JdbcQueryRunner(fixture.connection).queryOne("SELECT value", row -> row.getInt(1));
            assertEquals(count == 0 ? null : Integer.valueOf(1), value);
            fixture.assertClosed(true);
        }
    }

    @Test void ordinaryQueriesAndUpdatesCloseTheirResources() {
        Jdbc query = new Jdbc(2);
        assertEquals(List.of(1, 2), new JdbcQueryRunner(query.connection).query("SELECT value", row -> row.getInt(1)));
        query.assertClosed(true);
        Jdbc update = new Jdbc(0);
        assertEquals(3, new JdbcQueryRunner(update.connection).update("UPDATE items SET value = ?", 7));
        update.assertClosed(false);
    }

    @Test void sqlBindingFailureClosesStatementAndKeepsItsCause() {
        Jdbc fixture = new Jdbc(0);
        SQLException binding = new SQLException("Cannot bind parameter");
        fixture.bindingFailure = binding;
        DbCoreException failure = assertThrows(DbCoreException.class, () ->
            new JdbcQueryRunner(fixture.connection).query("SELECT ?", row -> 1, new Object()));
        assertSame(binding, failure.getCause());
        fixture.assertClosed(false);
    }

    @Test void uncheckedBindingFailureClosesStatement() {
        Jdbc fixture = new Jdbc(0);
        IllegalArgumentException binding = new IllegalArgumentException("Unsupported parameter");
        fixture.bindingFailure = binding;
        assertSame(binding, assertThrows(IllegalArgumentException.class, () ->
            new JdbcQueryRunner(fixture.connection).queryOne("SELECT ?", row -> 1, new Object())));
        fixture.assertClosed(false);
    }

    @Test void errorWhileBindingUpdateClosesStatement() {
        Jdbc fixture = new Jdbc(0);
        AssertionError binding = new AssertionError("Binding error");
        fixture.bindingFailure = binding;
        assertSame(binding, assertThrows(AssertionError.class, () ->
            new JdbcQueryRunner(fixture.connection).update("UPDATE items SET value = ?", new Object())));
        fixture.assertClosed(false);
    }

    @Test void statementCleanupFailureIsSuppressedOnOriginalBindingFailure() {
        for (Throwable cleanup : new Throwable[]{new SQLException("Close failed"), new IllegalStateException("Close failed")}) {
            Jdbc fixture = new Jdbc(0);
            SQLException binding = new SQLException("Bind failed");
            fixture.bindingFailure = binding;
            fixture.closeFailure = cleanup;
            DbCoreException failure = assertThrows(DbCoreException.class, () ->
                new JdbcQueryRunner(fixture.connection).update("UPDATE items SET value = ?", new Object()));
            assertSame(binding, failure.getCause());
            assertArrayEquals(new Throwable[]{cleanup}, binding.getSuppressed());
            fixture.assertClosed(false);
        }
    }

    @Test void mappingFailureClosesResultAndStatementWithoutClosingConnection() {
        Jdbc fixture = new Jdbc(1);
        SQLException mapping = new SQLException("Cannot map row");
        DbCoreException failure = assertThrows(DbCoreException.class, () ->
            new JdbcQueryRunner(fixture.connection).queryOne("SELECT value", row -> { throw mapping; }));
        assertSame(mapping, failure.getCause());
        fixture.assertClosed(true);
    }

    private static final class Jdbc {
        int rows, nextCalls, statementCloses, resultCloses, connectionCloses;
        Throwable bindingFailure, closeFailure;
        final ResultSet result;
        final PreparedStatement statement;
        final Connection connection;

        Jdbc(int rows) {
            this.rows = rows;
            result = proxy(ResultSet.class, (p, method, args) -> switch (method.getName()) {
                case "next" -> ++nextCalls <= this.rows;
                case "getInt" -> nextCalls;
                case "close" -> { resultCloses++; yield null; }
                default -> defaultValue(method.getReturnType());
            });
            statement = proxy(PreparedStatement.class, (p, method, args) -> switch (method.getName()) {
                case "executeQuery" -> result;
                case "executeUpdate" -> 3;
                case "setObject", "setTimestamp" -> {
                    if (bindingFailure != null) throw bindingFailure;
                    yield null;
                }
                case "close" -> {
                    statementCloses++;
                    if (closeFailure != null) throw closeFailure;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
            connection = proxy(Connection.class, (p, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> statement;
                case "close" -> { connectionCloses++; yield null; }
                default -> defaultValue(method.getReturnType());
            });
        }

        void assertClosed(boolean hadResult) {
            assertEquals(1, statementCloses, "Statement must close exactly once");
            assertEquals(hadResult ? 1 : 0, resultCloses);
            assertEquals(0, connectionCloses, "Connection belongs to the transaction");
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        return null;
    }
}
