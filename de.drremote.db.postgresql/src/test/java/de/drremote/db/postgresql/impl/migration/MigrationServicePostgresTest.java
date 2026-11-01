package de.drremote.db.postgresql.impl.migration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.drremote.db.postgresql.api.Migration;
import de.drremote.db.postgresql.api.PluginMigrationDescriptor;
import de.drremote.db.postgresql.api.ScriptMigration;
import de.drremote.db.postgresql.impl.jdbc.PostgresTestTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DRREMOTE_TEST_POSTGRES_URL", matches = ".+")
class MigrationServicePostgresTest {
    @TempDir Path directory;
    private HikariDataSource dataSource;
    private MigrationServiceImpl service;
    private String plugin;
    private String schema;
    private String table;

    @BeforeEach
    void names() {
        String id = UUID.randomUUID().toString().replace("-", "");
        plugin = "pgtest_" + id;
        schema = "schema_" + id;
        table = "item_" + id;
    }

    private void start(int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("DRREMOTE_TEST_POSTGRES_URL"));
        config.setUsername(System.getenv().getOrDefault("DRREMOTE_TEST_POSTGRES_USER", "postgres"));
        config.setPassword(System.getenv().getOrDefault("DRREMOTE_TEST_POSTGRES_PASSWORD", "postgres"));
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(500);
        dataSource = new HikariDataSource(config);
        service = new MigrationServiceImpl();
        service.bindDataSource(dataSource);
        service.bindTransactionManager(PostgresTestTransactions.using(dataSource));
        service.activate(null, Map.of(
            "jdbcUrl", config.getJdbcUrl(), "username", config.getUsername(),
            "password", config.getPassword()));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            statement.execute("DROP TABLE IF EXISTS core." + table);
            try (var delete = connection.prepareStatement(
                    "DELETE FROM core.schema_migrations WHERE plugin_name = ?")) {
                delete.setString(1, plugin);
                delete.executeUpdate();
            }
        } finally {
            dataSource.close();
        }
    }

    @Test
    void createsFreshPluginSchemaBeforeUnqualifiedJdbcMigration() throws Exception {
        start(2);
        service.migratePlugin(descriptor("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)"));
        assertNotNull(relation(schema + "." + table));
        assertNull(relation("core." + table));
    }

    @Test
    void schemaQualifiedMigrationWorksOnFreshDatabase() throws Exception {
        start(2);
        service.migratePlugin(descriptor("CREATE TABLE " + schema + "." + table + " (id INTEGER PRIMARY KEY)"));
        assertNotNull(relation(schema + "." + table));
    }

    @Test
    void oneConnectionPoolCanMigrateAndReplayWithoutStarvation() throws Exception {
        start(1);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var descriptor = descriptor("CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)");
        service.migratePlugin(descriptor);
        service.migratePlugin(descriptor);
        assertNotNull(relation(schema + "." + table));
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT COUNT(*) FROM core.schema_migrations WHERE plugin_name = ?")) {
            statement.setString(1, plugin);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test
    @Timeout(15)
    void mixedJdbcAndPsqlMigrationsCommitBetweenVersionsWithOneConnection() throws Exception {
        start(1);
        Path script = directory.resolve("insert.sql");
        Files.writeString(script, "INSERT INTO " + table + " VALUES (1);\n");
        ScriptMigration insert = new ScriptMigration() {
            public int getVersion() { return 2; }
            public String getDescription() { return "Insert from psql"; }
            public URL getScriptUrl() {
                try { return script.toUri().toURL(); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        };
        service.migratePlugin(descriptor(List.of(
            migration(1, "CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)"),
            insert,
            migration(3, "INSERT INTO " + table + " VALUES (2)"))));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + schema + "." + table)) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    @Timeout(20)
    void complexPsqlScriptSupportsProceduralSqlMetaCommandsCopyAndReplay() throws Exception {
        start(1);
        String sql;
        try (var input = getClass().getResourceAsStream("/migrations/complex-psql.sql")) {
            assertNotNull(input);
            sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("${table}", table);
        }
        var descriptor = descriptor(List.of(script(1, sql, true)));
        service.migratePlugin(descriptor);
        service.migratePlugin(descriptor);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT " + schema + ".adjusted_value(5), COUNT(*) FROM " + schema + "." + table)) {
            assertTrue(rows.next());
            assertEquals(15, rows.getInt(1));
            assertEquals(4, rows.getInt(2));
        }
        assertEquals(1, recordedVersions());
    }

    @Test
    @Timeout(20)
    void failedTransactionalPsqlScriptRollsBackAndDoesNotRecordVersion() throws Exception {
        start(1);
        var migration = script(1, "CREATE TABLE " + table + " (id INTEGER);\n"
            + "INSERT INTO " + table + " VALUES (1);\n"
            + "DO $failure$ BEGIN RAISE EXCEPTION 'deliberate script failure'; END; $failure$;\n"
            + "INSERT INTO " + table + " VALUES (2);\n", true);
        assertThrows(RuntimeException.class, () -> service.migratePlugin(descriptor(List.of(migration))));
        assertNull(relation(schema + "." + table));
        assertEquals(0, recordedVersions());
    }

    @Test
    @Timeout(20)
    void explicitTransactionOptOutSupportsConcurrentIndexCreation() throws Exception {
        start(1);
        var descriptor = descriptor(List.of(
            migration(1, "CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)"),
            script(2, "CREATE INDEX CONCURRENTLY " + table + "_idx ON " + table + " (id);\n", false),
            migration(3, "INSERT INTO " + table + " VALUES (1)")));
        service.migratePlugin(descriptor);
        service.migratePlugin(descriptor);
        assertNotNull(relation(schema + "." + table + "_idx"));
        assertEquals(3, recordedVersions());
    }

    @Test
    @Timeout(20)
    void defaultTransactionalScriptRejectsConcurrentIndexAndKeepsCompletedVersions() throws Exception {
        start(1);
        var descriptor = descriptor(List.of(
            migration(1, "CREATE TABLE " + table + " (id INTEGER PRIMARY KEY)"),
            script(2, "CREATE INDEX CONCURRENTLY " + table + "_idx ON " + table + " (id);\n", true)));
        assertThrows(RuntimeException.class, () -> service.migratePlugin(descriptor));
        assertNotNull(relation(schema + "." + table));
        assertNull(relation(schema + "." + table + "_idx"));
        assertEquals(1, recordedVersions());
    }

    private ScriptMigration script(int version, String sql, boolean singleTransaction) throws Exception {
        Path path = directory.resolve("migration-" + version + ".sql");
        Files.writeString(path, sql);
        URL url = path.toUri().toURL();
        return new ScriptMigration() {
            public int getVersion() { return version; }
            public String getDescription() { return "Real psql script " + version; }
            public URL getScriptUrl() { return url; }
            public boolean isSingleTransaction() { return singleTransaction; }
        };
    }

    private int recordedVersions() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM core.schema_migrations WHERE plugin_name = ?")) {
            statement.setString(1, plugin);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private String relation(String name) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT to_regclass(?)::text")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private PluginMigrationDescriptor descriptor(String sql) {
        return descriptor(List.of(migration(1, sql)));
    }

    private static Migration migration(int version, String sql) {
        return new Migration() {
            public int getVersion() { return version; }
            public String getDescription() { return "Test migration " + version; }
            public String getSql() { return sql; }
        };
    }

    private PluginMigrationDescriptor descriptor(List<Migration> migrations) {
        return new PluginMigrationDescriptor() {
            public String getPluginName() { return plugin; }
            public String getSchemaName() { return schema; }
            public List<Migration> getMigrations() {
                return migrations;
            }
        };
    }
}
