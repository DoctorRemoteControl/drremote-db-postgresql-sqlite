package de.drremote.db.postgresql.impl.migration;

import de.drremote.db.postgresql.api.*;
import org.osgi.service.component.annotations.*;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component(
    service = MigrationService.class,
    immediate = true,
    configurationPid = "de.drremote.db.postgresql"
)
public final class MigrationServiceImpl implements MigrationService {

    private static final Logger LOG = Logger.getLogger(MigrationServiceImpl.class.getName());

    private volatile TransactionManager tx;
    private final PsqlRunner psql = new PsqlRunner();

    // ConfigAdmin values (defaults)
    private volatile String jdbcUrl  = "jdbc:postgresql://localhost:5432/postgres";
    private volatile String username = "postgres";
    private volatile String password = "postgres";

    @Activate
    void activate(Map<String, Object> props) {
        applyConfig(props);
    }

    @Modified
    void modified(Map<String, Object> props) {
        applyConfig(props);
    }

    private void applyConfig(Map<String, Object> props) {
        // bevorzugt kurze Keys, fallback auf alte dotted Keys
        this.jdbcUrl  = getString2(props, "jdbcUrl",  "dbcore.jdbcUrl",  this.jdbcUrl);
        this.username = getString2(props, "username", "dbcore.username", this.username);
        this.password = getString2(props, "password", "dbcore.password", this.password);
    }

    private static String getString2(Map<String, Object> props, String k1, String k2, String def) {
        Object v = props != null ? props.get(k1) : null;
        if (v == null && props != null) v = props.get(k2);
        return v == null ? def : String.valueOf(v).trim();
    }

    @Reference
    void bindTransactionManager(TransactionManager tm) {
        this.tx = tm;
        ensureCoreSchema();
    }

    // ===============================
    // PUBLIC API
    // ===============================

    @Override
    public void migrateCoreSchema(List<Migration> migrations) throws DbCoreException {
        applyMigrations("core", "core", migrations);
    }

    @Override
    public void migratePlugin(PluginMigrationDescriptor descriptor) throws DbCoreException {
        applyMigrations(
            descriptor.getPluginName(),
            descriptor.getSchemaName(),
            descriptor.getMigrations()
        );
    }

    // ===============================
    // CORE SCHEMA
    // ===============================

    private void ensureCoreSchema() {
        TransactionManager tm = this.tx;
        if (tm == null) {
            throw new IllegalStateException("TransactionManager not bound");
        }

        tm.inTransaction(handle -> {
            try (Statement st = handle.getConnection().createStatement()) {
                st.execute("CREATE SCHEMA IF NOT EXISTS core");
                st.execute("""
                    CREATE TABLE IF NOT EXISTS core.schema_migrations (
                        plugin_name TEXT NOT NULL,
                        schema_name TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        sql TEXT,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        PRIMARY KEY (plugin_name, version)
                    )
                """);
            }
            return null;
        });
    }

    // ===============================
    // MIGRATION ENGINE
    // ===============================

    private void applyMigrations(String pluginName, String schemaName, List<Migration> migrations) {
        LOG.log(Level.FINE, () -> "applyMigrations plugin=" + pluginName
            + " schema=" + schemaName
            + " migrations=" + (migrations == null ? "null" : migrations.size()));

        if (migrations == null || migrations.isEmpty()) {
            LOG.fine("no migrations -> return");
            return;
        }

        TransactionManager tm = this.tx;
        if (tm == null) {
            throw new IllegalStateException("TransactionManager not bound");
        }

        List<Migration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(Migration::getVersion));

        // Use configured values (copy to locals for consistency during loop)
        final String jdbcUrlLocal = this.jdbcUrl;
        final String usernameLocal = this.username;
        final String passwordLocal = this.password;

        for (Migration m : sorted) {

            boolean already = tm.inTransaction(handle ->
                isAlreadyApplied(handle.getConnection(), pluginName, m.getVersion())
            );

            if (already) {
                LOG.log(Level.FINE, () -> "skipping v" + m.getVersion());
                continue;
            }

            LOG.log(Level.FINE, () -> "will apply v" + m.getVersion());

            if (!(m instanceof ScriptMigration sm)) {
                throw new IllegalStateException("Only ScriptMigration supported: " + m.getClass());
            }

            String sqlText = readSqlText(sm);

            runPsqlMigration(jdbcUrlLocal, usernameLocal, passwordLocal, schemaName, sm);

            tm.inTransaction(handle -> {
                insertMigrationRow(
                    handle.getConnection(),
                    pluginName,
                    schemaName,
                    m,
                    sqlText
                );
                return null;
            });
        }
    }

    // ===============================
    // SCRIPT EXECUTION
    // ===============================

    private void runPsqlMigration(
        String jdbcUrl,
        String username,
        String password,
        String schemaName,
        ScriptMigration migration
    ) {
        URL url = migration.getScriptUrl();
        if (url == null) {
            throw new IllegalStateException("Migration script URL is null for v" + migration.getVersion());
        }

        Path tmp = null;
        try {
            tmp = Files.createTempFile(
                "db-postgresql-" + schemaName + "-v" + migration.getVersion() + "-",
                ".sql"
            );

            try (InputStream in = url.openStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            psql.runSqlFile(jdbcUrl, username, password, schemaName, tmp);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply migration script v" + migration.getVersion(), e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            }
        }
    }

    // ===============================
    // SQL LOADING
    // ===============================

    private String readSqlText(ScriptMigration migration) {
        try (InputStream in = migration.getScriptUrl().openStream()) {
            if (in == null) {
                throw new IllegalStateException("Cannot read SQL from " + migration.getScriptUrl());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to read SQL text for migration v" + migration.getVersion(),
                e
            );
        }
    }

    // ===============================
    // MIGRATION STATE
    // ===============================

    private boolean isAlreadyApplied(Connection conn, String pluginName, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT 1
            FROM core.schema_migrations
            WHERE plugin_name = ? AND version = ?
        """)) {
            ps.setString(1, pluginName);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertMigrationRow(
        Connection conn,
        String pluginName,
        String schemaName,
        Migration migration,
        String sqlText
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO core.schema_migrations
                (plugin_name, schema_name, version, description, sql)
            VALUES (?,?,?,?,?)
        """)) {
            ps.setString(1, pluginName);
            ps.setString(2, schemaName);
            ps.setInt(3, migration.getVersion());
            ps.setString(4, migration.getDescription());
            ps.setString(5, sqlText);
            ps.executeUpdate();
        }
    }
}
