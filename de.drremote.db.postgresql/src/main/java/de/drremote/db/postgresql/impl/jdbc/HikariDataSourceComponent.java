package de.drremote.db.postgresql.impl.jdbc;

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
    configurationPid = "de.drremote.db.postgresql",
    property = "drremote.db.kind=postgresql"
)
public final class HikariDataSourceComponent implements DataSource {

    private static final Logger LOG = Logger.getLogger(HikariDataSourceComponent.class.getName());

    private volatile HikariDataSource delegate;

    @Activate
    void activate(Map<String, Object> props) {
        // OSGi requirement: explicitly load the JDBC driver
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("PostgreSQL JDBC Driver not found (OSGi classloader)", e);
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
        String username = getString(props, "username", null);
        String password = getString(props, "password", null);
        int maxPoolSize = getInt(props, "maxPoolSize", 10);
        int minIdle = getInt(props, "minIdle", 1);
        String poolName = getString(props, "poolName", "db-postgresql-pool");
        boolean registerMbeans = getBoolean(props, "registerMbeans", false);

        // optional: support legacy keys as fallback
        jdbcUrl = getString(props, "jdbcUrl", getString(props, "dbcore.jdbcUrl", jdbcUrl));
        username = getString(props, "username", getString(props, "dbcore.username", username));
        password = getString(props, "password", getString(props, "dbcore.password", password));

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("Missing required config property: jdbcUrl");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Missing required config property: username");
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl);
        hc.setDriverClassName("org.postgresql.Driver");
        hc.setUsername(username);
        hc.setPassword(password);
        hc.setMaximumPoolSize(maxPoolSize);
        hc.setMinimumIdle(minIdle);
        hc.setPoolName(poolName);
        hc.setRegisterMbeans(registerMbeans);

        HikariDataSource oldDelegate = delegate;
        // Hikari registers MBeans during startup. A same-name replacement would
        // collide with the running pool and then lose its MBeans on old-pool close.
        if (registerMbeans && oldDelegate != null && oldDelegate.isRegisterMbeans()
                && poolName.equals(oldDelegate.getPoolName())) {
            throw new IllegalStateException("JMX poolName is already active: " + poolName
                + "; change poolName when replacing an enabled pool, or restart the component");
        }
        HikariDataSource newDelegate = new HikariDataSource(hc);
        delegate = newDelegate;
        closeQuietly(oldDelegate);
    }

    private synchronized void closeDelegate() {
        HikariDataSource ds = delegate;
        delegate = null;
        closeQuietly(ds);
    }

    private static void closeQuietly(HikariDataSource ds) {
        if (ds != null) {
            try { ds.close(); } catch (Exception ignore) {}
        }
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
        Object value = props == null ? null : props.get(key);
        if (value == null) return def;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value).trim());
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
