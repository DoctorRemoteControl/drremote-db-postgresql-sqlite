package de.drremote.db.postgresql.impl.migration;

import de.drremote.db.postgresql.api.*;
import de.drremote.db.postgresql.impl.jdbc.JdbcDbHandle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
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
import javax.sql.DataSource;

@Component(
    service = MigrationService.class,
    immediate = true,
    configurationPid = "de.drremote.db.postgresql"
)
public final class MigrationServiceImpl implements MigrationService {

    private static final Logger LOG = Logger.getLogger(MigrationServiceImpl.class.getName());

    private volatile TransactionManager tx;
    private DataSource dataSource;
    private final PsqlRunner psql = new PsqlRunner();
    private final List<PluginMigrationDescriptor> pending = new ArrayList<>();
    private final Map<PluginMigrationDescriptor, ServiceRegistration<DatabaseReady>> readyServices =
        new IdentityHashMap<>();
    private BundleContext context;

    // ConfigAdmin values (defaults)
    private volatile String jdbcUrl  = "jdbc:postgresql://localhost:5432/postgres";
    private volatile String username = "postgres";
    private volatile String password = "postgres";
    private volatile String psqlExecutable = "psql";
    private volatile int psqlConnectTimeoutSeconds = 30;
    private volatile boolean migrationsEnabled = true;

    @Activate
    synchronized void activate(BundleContext context, Map<String, Object> props) {
        this.context = context;
        applyConfig(props);
        if (!migrationsEnabled) {
            LOG.info("PostgreSQL automatic migrations are disabled");
            return;
        }
        runPendingMigrations();
    }

    @Deactivate
    synchronized void deactivate() {
        readyServices.values().forEach(ServiceRegistration::unregister);
        readyServices.clear();
        pending.clear();
        context = null;
    }

    @Modified
    synchronized void modified(Map<String, Object> props) {
        boolean wasEnabled = migrationsEnabled;
        applyConfig(props);
        if (!wasEnabled && migrationsEnabled && context != null) {
            runPendingMigrations();
        }
    }

    private void runPendingMigrations() {
        ensureCoreSchema();
        List<PluginMigrationDescriptor> descriptors = List.copyOf(pending);
        pending.clear();
        descriptors.forEach(this::migrateAndPublish);
    }

    private void applyConfig(Map<String, Object> props) {
        // Prefer short keys and fall back to the legacy dotted keys.
        this.jdbcUrl  = getString2(props, "jdbcUrl",  "dbcore.jdbcUrl",  this.jdbcUrl);
        this.username = getString2(props, "username", "dbcore.username", this.username);
        this.password = getString2(props, "password", "dbcore.password", this.password);
        this.psqlExecutable = getString2(
            props,
            "psqlExecutable",
            "dbcore.psqlExecutable",
            this.psqlExecutable
        );
        this.psqlConnectTimeoutSeconds = getInt2(
            props,
            "psqlConnectTimeoutSeconds",
            "dbcore.psqlConnectTimeoutSeconds",
            this.psqlConnectTimeoutSeconds
        );
        this.migrationsEnabled = getBoolean2(
            props,
            "migrationsEnabled",
            "dbcore.migrationsEnabled",
            this.migrationsEnabled
        );
    }

    private static String getString2(Map<String, Object> props, String k1, String k2, String def) {
        Object v = props != null ? props.get(k1) : null;
        if (v == null && props != null) v = props.get(k2);
        return v == null ? def : String.valueOf(v).trim();
    }

    private static int getInt2(Map<String, Object> props, String k1, String k2, int def) {
        Object v = props != null ? props.get(k1) : null;
        if (v == null && props != null) v = props.get(k2);
        if (v == null) {
            return def;
        }
        int parsed;
        if (v instanceof Number n) {
            parsed = n.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(v).trim());
            } catch (Exception e) {
                return def;
            }
        }
        return parsed > 0 ? parsed : def;
    }

    private static boolean getBoolean2(Map<String, Object> props, String k1, String k2, boolean def) {
        Object value = props != null ? props.get(k1) : null;
        if (value == null && props != null) value = props.get(k2);
        if (value == null) return def;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        return def;
    }

    @Reference
    void bindTransactionManager(TransactionManager tm) {
        this.tx = tm;
    }

    @Reference(target = "(drremote.db.kind=postgresql)")
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Reference(
        service = PluginMigrationDescriptor.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    synchronized void addMigrationDescriptor(PluginMigrationDescriptor descriptor) {
        if (context == null || !migrationsEnabled) {
            pending.add(descriptor);
            return;
        }
        migrateAndPublish(descriptor);
    }

    synchronized void removeMigrationDescriptor(PluginMigrationDescriptor descriptor) {
        pending.removeIf(candidate -> candidate == descriptor);
        ServiceRegistration<DatabaseReady> registration = readyServices.remove(descriptor);
        if (registration != null) {
            registration.unregister();
        }
    }

    // ===============================
    // PUBLIC API
    // ===============================

    @Override
    public void migrateCoreSchema(List<Migration> migrations) throws DbCoreException {
        requireMigrationsEnabled();
        applyMigrations("core", "core", migrations);
    }

    @Override
    public void migratePlugin(PluginMigrationDescriptor descriptor) throws DbCoreException {
        requireMigrationsEnabled();
        applyMigrations(
            descriptor.getPluginName(),
            descriptor.getSchemaName(),
            descriptor.getMigrations()
        );
    }

    private void requireMigrationsEnabled() {
        if (!migrationsEnabled) {
            throw new DbCoreException("PostgreSQL automatic migrations are disabled by configuration");
        }
    }

    private void migrateAndPublish(PluginMigrationDescriptor descriptor) {
        if (readyServices.containsKey(descriptor)) {
            return;
        }

        String pluginName = requireText(descriptor.getPluginName(), "plugin name");
        String schemaName = requireText(descriptor.getSchemaName(), "schema name");
        try {
            migratePlugin(descriptor);

            DatabaseReady ready = new ReadyService(pluginName, schemaName);
            Dictionary<String, Object> properties = new Hashtable<>();
            properties.put(DatabaseReady.PROPERTY_KIND, ready.getDatabaseKind());
            properties.put(DatabaseReady.PROPERTY_PLUGIN, ready.getPluginName());
            properties.put(DatabaseReady.PROPERTY_SCHEMA, ready.getSchemaName());
            readyServices.put(
                descriptor,
                context.registerService(DatabaseReady.class, ready, properties)
            );
            LOG.info(() -> "PostgreSQL migrations ready: plugin=" + pluginName
                + " schema=" + schemaName);
        } catch (RuntimeException exception) {
            LOG.log(Level.SEVERE, "PostgreSQL migration failed: plugin=" + pluginName
                + " schema=" + schemaName, exception);
            throw exception;
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Migration descriptor " + label + " must not be blank");
        }
        return value.trim();
    }

    private record ReadyService(String pluginName, String schemaName) implements DatabaseReady {
        @Override
        public String getDatabaseKind() {
            return "postgresql";
        }

        @Override
        public String getPluginName() {
            return pluginName;
        }

        @Override
        public String getSchemaName() {
            return schemaName;
        }
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
        final String psqlExecutableLocal = this.psqlExecutable;
        final int psqlConnectTimeoutSecondsLocal = this.psqlConnectTimeoutSeconds;

        Connection lockConn = null;
        String lockKey = pluginName + ":" + schemaName;
        try {
            lockConn = acquireMigrationLock(lockKey);
            // Keep locking and migration work on the same borrowed connection.
            // Reserving another pool connection here deadlocks a one-connection pool.
            tm = migrationTransactions(lockConn);

            for (Migration m : sorted) {
                if (m instanceof ScriptMigration sm) {
                    applyScriptMigration(
                        tm,
                        pluginName,
                        schemaName,
                        sm,
                        jdbcUrlLocal,
                        usernameLocal,
                        passwordLocal,
                        psqlExecutableLocal,
                        psqlConnectTimeoutSecondsLocal
                    );
                } else {
                    applyJdbcMigration(tm, pluginName, schemaName, m);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire migration lock", e);
        } finally {
            if (lockConn != null) {
                try {
                    releaseMigrationLock(lockConn, lockKey);
                } catch (Exception ignore) {
                }
                try {
                    lockConn.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    // ===============================
    // SCRIPT EXECUTION
    // ===============================

    private void applyScriptMigration(
        TransactionManager tm,
        String pluginName,
        String schemaName,
        ScriptMigration migration,
        String jdbcUrl,
        String username,
        String password,
        String psqlExecutable,
        int psqlConnectTimeoutSeconds
    ) {
        boolean already = tm.inTransaction(handle ->
            isAlreadyApplied(handle.getConnection(), pluginName, migration.getVersion())
        );

        if (already) {
            LOG.log(Level.FINE, () -> "skipping v" + migration.getVersion());
            return;
        }

        LOG.log(Level.FINE, () -> "will apply v" + migration.getVersion());

        String sqlText = readSqlText(migration);
        runPsqlMigration(
            jdbcUrl,
            username,
            password,
            schemaName,
            migration,
            psqlExecutable,
            psqlConnectTimeoutSeconds
        );

        tm.inTransaction(handle -> {
            insertMigrationRow(
                handle.getConnection(),
                pluginName,
                schemaName,
                migration,
                sqlText
            );
            return null;
        });
    }

    private void runPsqlMigration(
        String jdbcUrl,
        String username,
        String password,
        String schemaName,
        ScriptMigration migration,
        String psqlExecutable,
        int psqlConnectTimeoutSeconds
    ) {
        URL url = scriptUrl(migration);

        Path tmp = null;
        try {
            tmp = Files.createTempFile(
                tempFilePrefix(schemaName, migration.getVersion()),
                ".sql"
            );

            try (InputStream in = url.openStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            psql.runSqlFile(
                psqlExecutable,
                jdbcUrl,
                username,
                password,
                schemaName,
                tmp,
                migration.isSingleTransaction(),
                psqlConnectTimeoutSeconds
            );

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
        URL url = scriptUrl(migration);
        try (InputStream in = url.openStream()) {
            if (in == null) {
                throw new IllegalStateException("Cannot read SQL from " + url);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to read SQL text for migration v" + migration.getVersion(),
                e
            );
        }
    }

    private URL scriptUrl(ScriptMigration migration) {
        URL url = migration.getScriptUrl();
        if (url == null) {
            throw new IllegalStateException("Migration script URL is null for v" + migration.getVersion());
        }
        return url;
    }

    private String tempFilePrefix(String schemaName, int version) {
        String safeSchema = schemaName == null ? "schema" : schemaName.replaceAll("[^A-Za-z0-9._-]", "_");
        String prefix = "db-postgresql-" + safeSchema + "-v" + version + "-";
        return prefix.length() >= 3 ? prefix : "db-";
    }

    private void runJdbcMigration(
        TransactionManager tm,
        String pluginName,
        String schemaName,
        Migration migration,
        String sqlText
    ) {
        if (sqlText == null || sqlText.isBlank()) {
            throw new IllegalStateException("Migration SQL is blank for v" + migration.getVersion());
        }

        tm.inTransaction(handle -> {
            Connection conn = handle.getConnection();
            if (isAlreadyApplied(conn, pluginName, migration.getVersion())) {
                LOG.log(Level.FINE, () -> "skipping v" + migration.getVersion());
                return null;
            }

            LOG.log(Level.FINE, () -> "will apply v" + migration.getVersion());

            handle.setSearchPath(schemaName);
            try (Statement st = conn.createStatement()) {
                // setSearchPath validates the identifier before it is used in DDL.
                st.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
                st.execute(sqlText);
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to apply JDBC migration v" + migration.getVersion(), e);
            }
            insertMigrationRow(conn, pluginName, schemaName, migration, sqlText);
            return null;
        });
    }

    private void applyJdbcMigration(
        TransactionManager tm,
        String pluginName,
        String schemaName,
        Migration migration
    ) {
        runJdbcMigration(tm, pluginName, schemaName, migration, migration.getSql());
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

    private TransactionManager migrationTransactions(Connection connection) {
        return new TransactionManager() {
            @Override
            public <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException {
                try {
                    connection.setAutoCommit(false);
                    try {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("SET search_path TO core, public");
                        }
                        T result = callback.doInTransaction(new JdbcDbHandle(connection));
                        connection.commit();
                        return result;
                    } catch (Exception | Error failure) {
                        try {
                            connection.rollback();
                        } catch (SQLException rollbackFailure) {
                            failure.addSuppressed(rollbackFailure);
                        }
                        throw failure;
                    }
                } catch (Exception failure) {
                    throw new DbCoreException("Migration transaction failed", failure);
                }
            }
        };
    }

    private Connection acquireMigrationLock(String lockKey) throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource not bound");
        }
        Connection conn = dataSource.getConnection();
        boolean locked = false;
        try {
            try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_lock(hashtext(?))")) {
                ps.setString(1, lockKey);
                ps.execute();
                locked = true;
            }
            return conn;
        } catch (SQLException | RuntimeException | Error failure) {
            if (locked) {
                try {
                    releaseMigrationLock(conn, lockKey);
                } catch (SQLException cleanupFailure) {
                    if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
                }
            }
            try {
                conn.close();
            } catch (SQLException closeFailure) {
                if (closeFailure != failure) failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private void releaseMigrationLock(Connection conn, String lockKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            ps.setString(1, lockKey);
            ps.execute();
        }
    }
}
