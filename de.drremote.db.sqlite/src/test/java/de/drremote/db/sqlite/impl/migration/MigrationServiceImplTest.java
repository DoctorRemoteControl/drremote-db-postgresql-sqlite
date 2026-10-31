package de.drremote.db.sqlite.impl.migration;

import de.drremote.db.sqlite.api.BatchMigration;
import de.drremote.db.sqlite.api.DbCoreException;
import de.drremote.db.sqlite.api.Migration;
import de.drremote.db.sqlite.api.MigrationBaseline;
import de.drremote.db.sqlite.api.PluginMigrationDescriptor;
import de.drremote.db.sqlite.api.TransactionCallback;
import de.drremote.db.sqlite.api.TransactionManager;
import de.drremote.db.sqlite.impl.jdbc.JdbcDbHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationServiceImplTest {
    private TestTransactionManager tx;
    private MigrationServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        tx = new TestTransactionManager();
        service = new MigrationServiceImpl();
        service.bindTransactionManager(tx);
    }

    @AfterEach
    void tearDown() throws Exception {
        tx.close();
    }

    @Test
    void appliesBatchAtomicallyAndRecordsOneVersion() throws Exception {
        service.migratePlugin(descriptor(
            "batch",
            List.of(batch(1, "table and index", List.of(
                "CREATE TABLE batch_item (id INTEGER PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE INDEX ix_batch_item_value ON batch_item(value)"
            ))),
            null
        ));

        assertTrue(objectExists("table", "batch_item"));
        assertTrue(objectExists("index", "ix_batch_item_value"));
        assertEquals(1, scalarInt(
            "SELECT COUNT(*) FROM core_schema_migrations WHERE plugin_name = 'batch'"
        ));
    }

    @Test
    void rollsBackWholeBatchWhenOneStatementFails() throws Exception {
        var descriptor = descriptor(
            "broken-batch",
            List.of(batch(1, "broken", List.of(
                "CREATE TABLE must_rollback (id INTEGER PRIMARY KEY)",
                "INSERT INTO missing_table(id) VALUES (1)"
            ))),
            null
        );

        assertThrows(DbCoreException.class, () -> service.migratePlugin(descriptor));
        assertFalse(objectExists("table", "must_rollback"));
        assertEquals(0, scalarInt(
            "SELECT COUNT(*) FROM core_schema_migrations WHERE plugin_name = 'broken-batch'"
        ));
    }

    @Test
    void usesBaselineOnlyForEmptyHistoryAndContinuesAboveItsVersion() throws Exception {
        List<Migration> migrations = List.of(
            migration(1, "legacy table", "CREATE TABLE legacy_only (id INTEGER PRIMARY KEY)"),
            migration(2, "legacy column", "ALTER TABLE app_item ADD COLUMN legacy_value TEXT"),
            migration(3, "current data", "INSERT INTO app_item(id, value) VALUES (1, 'current')")
        );
        MigrationBaseline baseline = baseline(2, "current schema", List.of(
            "CREATE TABLE app_item (id INTEGER PRIMARY KEY, value TEXT NOT NULL)",
            "CREATE INDEX ix_app_item_value ON app_item(value)"
        ));

        PluginMigrationDescriptor descriptor = descriptor("baseline", migrations, baseline);
        service.migratePlugin(descriptor);
        service.migratePlugin(descriptor);

        assertFalse(objectExists("table", "legacy_only"));
        assertTrue(objectExists("index", "ix_app_item_value"));
        assertEquals(2, scalarInt(
            "SELECT version FROM core_schema_baselines WHERE plugin_name = 'baseline'"
        ));
        assertEquals(1, scalarInt(
            "SELECT COUNT(*) FROM core_schema_migrations WHERE plugin_name = 'baseline'"
        ));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM app_item WHERE value = 'current'"));
    }

    @Test
    void ignoresProposedBaselineWhenIncrementalHistoryExists() throws Exception {
        List<Migration> oldMigrations = List.of(
            migration(1, "table", "CREATE TABLE upgrade_item (id INTEGER PRIMARY KEY)"),
            migration(2, "name", "ALTER TABLE upgrade_item ADD COLUMN name TEXT")
        );
        service.migratePlugin(descriptor("upgrade", oldMigrations, null));

        List<Migration> currentMigrations = List.of(
            oldMigrations.get(0),
            oldMigrations.get(1),
            migration(3, "data", "INSERT INTO upgrade_item(id, name) VALUES (1, 'upgraded')")
        );
        service.migratePlugin(descriptor(
            "upgrade",
            currentMigrations,
            baseline(2, "must not run", List.of("CREATE TABLE baseline_only (id INTEGER PRIMARY KEY)"))
        ));

        assertFalse(objectExists("table", "baseline_only"));
        assertEquals(0, scalarInt(
            "SELECT COUNT(*) FROM core_schema_baselines WHERE plugin_name = 'upgrade'"
        ));
        assertEquals(3, scalarInt(
            "SELECT COUNT(*) FROM core_schema_migrations WHERE plugin_name = 'upgrade'"
        ));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM upgrade_item WHERE name = 'upgraded'"));
    }

    @Test
    void keepsSingleStatementGuardForRegularMigrations() {
        PluginMigrationDescriptor descriptor = descriptor(
            "guard",
            List.of(migration(
                1,
                "two statements",
                "CREATE TABLE first_table(id INTEGER); CREATE TABLE second_table(id INTEGER)"
            )),
            null
        );

        assertThrows(DbCoreException.class, () -> service.migratePlugin(descriptor));
    }

    @Test
    void rejectsChangesToAlreadyAppliedMigrationSql() {
        service.migratePlugin(descriptor(
            "drift",
            List.of(migration(1, "original", "CREATE TABLE original_table(id INTEGER)")),
            null
        ));

        PluginMigrationDescriptor changed = descriptor(
            "drift",
            List.of(migration(1, "changed", "CREATE TABLE changed_table(id INTEGER)")),
            null
        );

        DbCoreException failure = assertThrows(
            DbCoreException.class,
            () -> service.migratePlugin(changed)
        );
        assertTrue(rootMessage(failure).contains("Migration drift detected for drift v1"));
    }

    private boolean objectExists(String type, String name) throws Exception {
        try (var ps = tx.connection.prepareStatement(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?"
        )) {
            ps.setString(1, type);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private int scalarInt(String sql) throws Exception {
        try (Statement statement = tx.connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    private static PluginMigrationDescriptor descriptor(
        String pluginName,
        List<Migration> migrations,
        MigrationBaseline baseline
    ) {
        return new PluginMigrationDescriptor() {
            @Override
            public String getPluginName() {
                return pluginName;
            }

            @Override
            public List<Migration> getMigrations() {
                return migrations;
            }

            @Override
            public MigrationBaseline getBaseline() {
                return baseline;
            }
        };
    }

    private static Migration migration(int version, String description, String sql) {
        return new SimpleMigration(version, description, sql);
    }

    private static BatchMigration batch(int version, String description, List<String> statements) {
        return new SimpleBatchMigration(version, description, statements);
    }

    private static MigrationBaseline baseline(int version, String description, List<String> statements) {
        return new SimpleBaseline(version, description, statements);
    }

    private record SimpleMigration(int version, String description, String sql) implements Migration {
        @Override public int getVersion() { return version; }
        @Override public String getDescription() { return description; }
        @Override public String getSql() { return sql; }
    }

    private record SimpleBatchMigration(
        int version,
        String description,
        List<String> statements
    ) implements BatchMigration {
        @Override public int getVersion() { return version; }
        @Override public String getDescription() { return description; }
        @Override public List<String> getStatements() { return statements; }
    }

    private record SimpleBaseline(
        int version,
        String description,
        List<String> statements
    ) implements MigrationBaseline {
        @Override public int getVersion() { return version; }
        @Override public String getDescription() { return description; }
        @Override public List<String> getStatements() { return statements; }
    }

    private static final class TestTransactionManager implements TransactionManager, AutoCloseable {
        private final Connection connection;

        private TestTransactionManager() throws Exception {
            connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        }

        @Override
        public synchronized <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException {
            try {
                connection.setAutoCommit(false);
                try {
                    T result = callback.doInTransaction(new JdbcDbHandle(connection));
                    connection.commit();
                    return result;
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception failure) {
                throw new DbCoreException("Test transaction failed", failure);
            }
        }

        @Override
        public void close() throws Exception {
            connection.close();
        }
    }
}
