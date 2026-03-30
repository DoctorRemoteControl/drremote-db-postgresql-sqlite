package de.drremote.db.sqlite.impl.migration;

import de.drremote.db.sqlite.api.*;
import org.osgi.service.component.annotations.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component(
    service = MigrationService.class,
    immediate = true
)
public final class MigrationServiceImpl implements MigrationService {

    private static final Logger LOG = Logger.getLogger(MigrationServiceImpl.class.getName());

    private volatile TransactionManager tx;

    @Reference
    void bindTransactionManager(TransactionManager tm) {
        this.tx = tm;
        ensureCoreSchema();
    }

    @Override
    public void migratePlugin(PluginMigrationDescriptor descriptor) throws DbCoreException {
        applyMigrations(descriptor.getPluginName(), descriptor.getMigrations());
    }

    private void ensureCoreSchema() {
        TransactionManager tm = this.tx;
        if (tm == null) {
            throw new IllegalStateException("TransactionManager not bound");
        }

        tm.inTransaction(handle -> {
            try (Statement st = handle.getConnection().createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS core_schema_migrations (
                        plugin_name TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        sql TEXT,
                        applied_at TEXT NOT NULL DEFAULT (datetime('now')),
                        PRIMARY KEY (plugin_name, version)
                    )
                """);
            }
            return null;
        });
    }

    private void applyMigrations(String pluginName, List<Migration> migrations) {
        LOG.log(Level.FINE, () -> "applyMigrations plugin=" + pluginName
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

        for (Migration m : sorted) {
            tm.inTransaction(handle -> {
                Connection conn = handle.getConnection();

                boolean already = isAlreadyApplied(conn, pluginName, m.getVersion());
                if (already) {
                    LOG.log(Level.FINE, () -> "skipping v" + m.getVersion());
                    return null;
                }

                String sqlText = m.getSql();
                if (sqlText == null || sqlText.isBlank()) {
                    throw new IllegalStateException("Migration SQL is empty for v" + m.getVersion());
                }

                String normalizedSql = sqlText.trim();
                if (normalizedSql.endsWith(";")) {
                    normalizedSql = normalizedSql.substring(0, normalizedSql.length() - 1).trim();
                }
                if (normalizedSql.isEmpty()) {
                    throw new IllegalStateException("Migration SQL is empty for v" + m.getVersion());
                }
                if (normalizedSql.contains(";")) {
                    throw new IllegalStateException(
                        "Migration SQL must contain exactly one statement for v" + m.getVersion()
                    );
                }

                LOG.log(Level.FINE, () -> "will apply v" + m.getVersion());

                try (Statement st = conn.createStatement()) {
                    st.execute(normalizedSql);
                }

                insertMigrationRow(conn, pluginName, m, normalizedSql);
                return null;
            });
        }
    }

    private boolean isAlreadyApplied(Connection conn, String pluginName, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT 1
            FROM core_schema_migrations
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
        Migration migration,
        String sqlText
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO core_schema_migrations
                (plugin_name, version, description, sql)
            VALUES (?,?,?,?)
        """)) {
            ps.setString(1, pluginName);
            ps.setInt(2, migration.getVersion());
            ps.setString(3, migration.getDescription());
            ps.setString(4, sqlText);
            ps.executeUpdate();
        }
    }
}
