package de.drremote.db.sqlite.impl.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.osgi.service.component.annotations.*;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

@Component(
    service = DataSource.class,
    immediate = true,
    configurationPid = "de.drremote.db.sqlite"
)
public final class HikariDataSourceComponent implements DataSource {

    private static final Logger LOG = Logger.getLogger(HikariDataSourceComponent.class.getName());

    private volatile HikariDataSource delegate;

    @Activate
    void activate(Map<String, Object> props) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC Driver not found (OSGi classloader)", e);
        }

        reconfigure(props);

        LOG.info("DataSource active: " + delegate.getJdbcUrl());
    }

    @Modified
    void modified(Map<String, Object> props) {
        reconfigure(props);
        LOG.info("DataSource reconfigured: " + delegate.getJdbcUrl());
    }

    @Deactivate
    void deactivate() {
        closeDelegate();
        LOG.info("DataSource stopped");
    }

    private synchronized void reconfigure(Map<String, Object> props) {
        String jdbcUrl = getString(props, "jdbcUrl", null);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("Missing required config property: jdbcUrl");
        }
        int maxPoolSize = getInt(props, "maxPoolSize", 1);
        int minIdle = getInt(props, "minIdle", 1);
        String poolName = getString(props, "poolName", "db-sqlite-pool");
        int busyTimeoutMs = getInt(props, "busyTimeoutMs", 5000);
        boolean foreignKeys = getBoolean(props, "foreignKeys", true);

        if (maxPoolSize != 1) {
            throw new IllegalStateException("SQLite requires maxPoolSize=1");
        }
        if (minIdle != 1) {
            throw new IllegalStateException("SQLite requires minIdle=1");
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setMaximumPoolSize(maxPoolSize);
        hc.setMinimumIdle(minIdle);
        hc.setPoolName(poolName);

        hc.addDataSourceProperty("busy_timeout", busyTimeoutMs);
        hc.addDataSourceProperty("foreign_keys", foreignKeys);

        closeDelegate();
        delegate = new HikariDataSource(hc);
    }

    private void closeDelegate() {
        HikariDataSource ds = delegate;
        if (ds != null) {
            try { ds.close(); } catch (Exception ignore) {}
        }
        delegate = null;
    }

    private static String getString(Map<String, Object> props, String key, String def) {
        Object v = props == null ? null : props.get(key);
        return (v == null) ? def : String.valueOf(v).trim();
    }

    private static int getInt(Map<String, Object> props, String key, int def) {
        Object v = props == null ? null : props.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); }
        catch (Exception e) { return def; }
    }

    private static boolean getBoolean(Map<String, Object> props, String key, boolean def) {
        Object v = props == null ? null : props.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v).trim());
    }

    // =====================
    // DataSource delegation
    // =====================

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }
}
