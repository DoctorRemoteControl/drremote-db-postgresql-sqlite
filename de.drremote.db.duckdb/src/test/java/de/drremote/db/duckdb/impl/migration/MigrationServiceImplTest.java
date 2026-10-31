package de.drremote.db.duckdb.impl.migration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.drremote.db.duckdb.api.*;
import de.drremote.db.duckdb.impl.jdbc.JdbcDbHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationServiceImplTest {
    @TempDir Path directory;
    private HikariDataSource dataSource;
    private TransactionManager transactions;
    private MigrationServiceImpl migrations;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:duckdb:" + directory.resolve("migrations.duckdb"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        transactions = new TransactionManager() {
            @Override public <T> T inTransaction(TransactionCallback<T> callback) {
                try (var connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        T result = callback.doInTransaction(new JdbcDbHandle(connection));
                        connection.commit();
                        return result;
                    } catch (Exception failure) {
                        connection.rollback();
                        throw failure;
                    }
                } catch (Exception failure) {
                    throw new DbCoreException(failure);
                }
            }
        };
        migrations = new MigrationServiceImpl();
        migrations.bindTransactionManager(transactions);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @Test
    void appliesInVersionOrderAndReplaysWithoutDuplicatingRows() {
        var descriptor = descriptor(List.of(
            migration(2, "INSERT INTO items VALUES (1, 'ready')"),
            migration(1, "CREATE TABLE items (id INTEGER PRIMARY KEY, label VARCHAR)")
        ));
        migrations.migratePlugin(descriptor);
        migrations.migratePlugin(descriptor);
        assertEquals(1L, count("items"));
        assertEquals(2L, count("core_schema_migrations"));
    }

    @Test
    void failedMigrationDoesNotRecordACompletedVersion() {
        assertThrows(DbCoreException.class, () -> migrations.migratePlugin(descriptor(
            List.of(migration(1, "INSERT INTO missing_table VALUES (1)")))));
        assertEquals(0L, count("core_schema_migrations"));
        migrations.migratePlugin(descriptor(List.of(migration(1, "CREATE TABLE items(id INTEGER)"))));
        assertEquals(1L, count("core_schema_migrations"));
    }

    @Test
    void rejectsMultipleStatementsBeforeApplyingEither() {
        assertThrows(DbCoreException.class, () -> migrations.migratePlugin(descriptor(List.of(
            migration(1, "CREATE TABLE first_item(id INTEGER); CREATE TABLE second_item(id INTEGER)")))));
        assertEquals(0L, count("core_schema_migrations"));
    }

    private long count(String table) {
        return transactions.inTransaction(db -> db.getQueryRunner().queryOne(
            "SELECT COUNT(*) FROM " + table, row -> row.getLong(1)));
    }

    private static Migration migration(int version, String sql) {
        return new Migration() {
            public int getVersion() { return version; }
            public String getDescription() { return "step " + version; }
            public String getSql() { return sql; }
        };
    }

    private static PluginMigrationDescriptor descriptor(List<Migration> steps) {
        return new PluginMigrationDescriptor() {
            public String getPluginName() { return "example"; }
            public List<Migration> getMigrations() { return steps; }
        };
    }
}
