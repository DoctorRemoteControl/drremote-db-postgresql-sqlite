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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        if (descriptor == null) {
            throw new IllegalArgumentException("Migration descriptor must not be null");
        }
        applyMigrations(
            descriptor.getPluginName(),
            descriptor.getMigrations(),
            descriptor.getBaseline()
        );
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
                st.execute("""
                    CREATE TABLE IF NOT EXISTS core_schema_baselines (
                        plugin_name TEXT PRIMARY KEY,
                        version INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        sql TEXT NOT NULL,
                        applied_at TEXT NOT NULL DEFAULT (datetime('now'))
                    )
                """);
            }
            return null;
        });
    }

    private void applyMigrations(
        String pluginName,
        List<Migration> migrations,
        MigrationBaseline proposedBaseline
    ) {
        LOG.log(Level.FINE, () -> "applyMigrations plugin=" + pluginName
            + " migrations=" + (migrations == null ? "null" : migrations.size()));

        if (pluginName == null || pluginName.isBlank()) {
            throw new IllegalArgumentException("Plugin name must not be blank");
        }

        if (migrations == null || migrations.isEmpty()) {
            if (proposedBaseline != null) {
                throw new IllegalArgumentException("A baseline requires incremental migrations");
            }
            LOG.fine("no migrations -> return");
            return;
        }

        TransactionManager tm = this.tx;
        if (tm == null) {
            throw new IllegalStateException("TransactionManager not bound");
        }

        List<Migration> sorted = new ArrayList<>(migrations);
        sorted.sort(Comparator.comparingInt(Migration::getVersion));
        validateVersions(sorted);
        validateBaseline(proposedBaseline, sorted);

        Integer baselineVersion = prepareBaseline(pluginName, proposedBaseline);

        for (Migration m : sorted) {
            if (baselineVersion != null && m.getVersion() <= baselineVersion) {
                int skippedVersion = m.getVersion();
                LOG.log(Level.FINE, () -> "skipping v" + skippedVersion + " covered by baseline v" + baselineVersion);
                continue;
            }

            tm.inTransaction(handle -> {
                Connection conn = handle.getConnection();

                AppliedMigration applied = findAppliedMigration(conn, pluginName, m.getVersion());
                if (applied.exists()) {
                    verifyAppliedSql(pluginName, m, applied.sql());
                    LOG.log(Level.FINE, () -> "skipping v" + m.getVersion());
                    return null;
                }

                List<String> statements = migrationStatements(m);
                String normalizedSql = String.join(";\n", statements);

                LOG.log(Level.FINE, () -> "will apply v" + m.getVersion());

                executeStatements(conn, statements);

                insertMigrationRow(conn, pluginName, m, normalizedSql);
                return null;
            });
        }
    }

    private Integer prepareBaseline(String pluginName, MigrationBaseline proposedBaseline) {
        TransactionManager tm = this.tx;
        return tm.inTransaction(handle -> {
            Connection conn = handle.getConnection();
            Integer appliedVersion = findAppliedBaselineVersion(conn, pluginName);
            if (appliedVersion != null) {
                return appliedVersion;
            }
            if (proposedBaseline == null || hasAppliedMigrations(conn, pluginName)) {
                return null;
            }

            List<String> statements = explicitStatements(
                proposedBaseline.getStatements(),
                "baseline v" + proposedBaseline.getVersion()
            );
            executeStatements(conn, statements);
            insertBaselineRow(conn, pluginName, proposedBaseline, String.join(";\n", statements));
            LOG.log(Level.INFO, () -> "applied baseline v" + proposedBaseline.getVersion()
                + " for plugin " + pluginName);
            return proposedBaseline.getVersion();
        });
    }

    private static void validateVersions(List<Migration> migrations) {
        Set<Integer> versions = new HashSet<>();
        for (Migration migration : migrations) {
            if (migration == null) {
                throw new IllegalArgumentException("Migration list must not contain null entries");
            }
            int version = migration.getVersion();
            if (version <= 0) {
                throw new IllegalArgumentException("Migration version must be positive: " + version);
            }
            if (!versions.add(version)) {
                throw new IllegalArgumentException("Duplicate migration version: " + version);
            }
        }
    }

    private static void validateBaseline(MigrationBaseline baseline, List<Migration> migrations) {
        if (baseline == null) {
            return;
        }
        int version = baseline.getVersion();
        boolean represented = migrations.stream().anyMatch(migration -> migration.getVersion() == version);
        if (version <= 0 || !represented) {
            throw new IllegalArgumentException(
                "Baseline version must match an incremental migration version: " + version
            );
        }
        if (baseline.getDescription() == null || baseline.getDescription().isBlank()) {
            throw new IllegalArgumentException("Baseline description must not be blank");
        }
        explicitStatements(baseline.getStatements(), "baseline v" + version);
    }

    private static List<String> migrationStatements(Migration migration) {
        if (migration instanceof BatchMigration batchMigration) {
            return explicitStatements(
                batchMigration.getStatements(),
                "migration v" + migration.getVersion()
            );
        }
        return List.of(singleStatement(migration.getSql(), migration.getVersion()));
    }

    private static List<String> explicitStatements(List<String> sqlStatements, String label) {
        if (sqlStatements == null || sqlStatements.isEmpty()) {
            throw new IllegalStateException("SQL statement list is empty for " + label);
        }
        List<String> normalized = new ArrayList<>(sqlStatements.size());
        for (String sql : sqlStatements) {
            String statement = trimTrailingSemicolon(sql);
            if (statement.isEmpty()) {
                throw new IllegalStateException("SQL statement is empty for " + label);
            }
            normalized.add(statement);
        }
        return List.copyOf(normalized);
    }

    private static String singleStatement(String sqlText, int version) {
        String normalizedSql = trimTrailingSemicolon(sqlText);
        if (normalizedSql.isEmpty()) {
            throw new IllegalStateException("Migration SQL is empty for v" + version);
        }
        if (normalizedSql.contains(";")) {
            throw new IllegalStateException(
                "Migration SQL must contain exactly one statement for v" + version
            );
        }
        return normalizedSql;
    }

    private static String trimTrailingSemicolon(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            return "";
        }
        String normalizedSql = sqlText.trim();
        if (normalizedSql.endsWith(";")) {
            normalizedSql = normalizedSql.substring(0, normalizedSql.length() - 1).trim();
        }
        return normalizedSql;
    }

    private static void executeStatements(Connection conn, List<String> statements) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    private AppliedMigration findAppliedMigration(
        Connection conn,
        String pluginName,
        int version
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT sql
            FROM core_schema_migrations
            WHERE plugin_name = ? AND version = ?
        """)) {
            ps.setString(1, pluginName);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                    ? new AppliedMigration(true, rs.getString("sql"))
                    : new AppliedMigration(false, null);
            }
        }
    }

    private void verifyAppliedSql(String pluginName, Migration migration, String appliedSql) {
        if (appliedSql == null || appliedSql.isBlank()) {
            LOG.log(Level.WARNING, () -> "cannot verify migration drift for " + pluginName
                + " v" + migration.getVersion() + ": stored SQL is empty");
            return;
        }
        String expectedSql = String.join(";\n", migrationStatements(migration));
        if (!comparableSql(appliedSql).equals(comparableSql(expectedSql))) {
            throw new IllegalStateException(
                "Migration drift detected for " + pluginName + " v" + migration.getVersion()
                    + "; restore the originally released SQL and add a new migration instead"
            );
        }
    }

    private static String comparableSql(String sql) {
        String normalized = sql.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private Integer findAppliedBaselineVersion(Connection conn, String pluginName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT version
            FROM core_schema_baselines
            WHERE plugin_name = ?
        """)) {
            ps.setString(1, pluginName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("version") : null;
            }
        }
    }

    private boolean hasAppliedMigrations(Connection conn, String pluginName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT 1
            FROM core_schema_migrations
            WHERE plugin_name = ?
            LIMIT 1
        """)) {
            ps.setString(1, pluginName);
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

    private void insertBaselineRow(
        Connection conn,
        String pluginName,
        MigrationBaseline baseline,
        String sqlText
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO core_schema_baselines
                (plugin_name, version, description, sql)
            VALUES (?,?,?,?)
        """)) {
            ps.setString(1, pluginName);
            ps.setInt(2, baseline.getVersion());
            ps.setString(3, baseline.getDescription());
            ps.setString(4, sqlText);
            ps.executeUpdate();
        }
    }

    private record AppliedMigration(boolean exists, String sql) {
    }
}
