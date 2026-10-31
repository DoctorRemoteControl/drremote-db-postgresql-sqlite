package de.drremote.db.duckdb.impl.jdbc;

import de.drremote.db.duckdb.api.DbCoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTransactionManagerTest {
    @TempDir Path directory;
    private HikariDataSourceComponent dataSource;
    private JdbcTransactionManager transactions;

    @BeforeEach
    void setUp() {
        dataSource = new HikariDataSourceComponent();
        dataSource.activate(Map.of("jdbcUrl", "jdbc:duckdb:" + directory.resolve("test.duckdb")));
        transactions = new JdbcTransactionManager();
        transactions.bindDataSource(dataSource);
        transactions.inTransaction(db -> {
            db.getQueryRunner().update("CREATE TABLE items (id INTEGER PRIMARY KEY, label VARCHAR)");
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) dataSource.deactivate();
    }

    @Test
    void commitsAndQueriesBoundParameters() {
        transactions.inTransaction(db -> {
            db.getQueryRunner().update("INSERT INTO items VALUES (?, ?)", 1, "first");
            return null;
        });
        String label = transactions.inTransaction(db -> db.getQueryRunner().queryOne(
            "SELECT label FROM items WHERE id = ?", row -> row.getString(1), 1));
        assertEquals("first", label);
    }

    @Test
    void rollsBackFailedWorkAndCanRunAnotherTransaction() {
        assertThrows(DbCoreException.class, () -> transactions.inTransaction(db -> {
            db.getQueryRunner().update("INSERT INTO items VALUES (?, ?)", 1, "rollback");
            throw new IllegalStateException("abort");
        }));
        long count = transactions.inTransaction(db -> db.getQueryRunner().queryOne(
            "SELECT COUNT(*) FROM items", row -> row.getLong(1)));
        assertEquals(0L, count);
        transactions.inTransaction(db -> {
            db.getQueryRunner().update("INSERT INTO items VALUES (?, ?)", 2, "after failure");
            return null;
        });
        assertEquals("after failure", transactions.inTransaction(db -> db.getQueryRunner().queryOne(
            "SELECT label FROM items WHERE id = ?", row -> row.getString(1), 2)));
    }

    @Test
    void closingHandleDoesNotCloseTheManagedConnection() {
        transactions.inTransaction(db -> {
            db.close();
            assertFalse(db.getConnection().isClosed());
            return null;
        });
    }
}
