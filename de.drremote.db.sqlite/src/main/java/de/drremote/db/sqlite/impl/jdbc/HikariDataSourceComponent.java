package de.drremote.db.sqlite.impl.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.osgi.service.component.annotations.*;

import javax.sql.DataSource;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;
import java.lang.management.ManagementFactory;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

@Component(
    service = DataSource.class,
    immediate = true,
    configurationPid = "de.drremote.db.sqlite",
    property = "drremote.db.kind=sqlite"
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
        boolean registerMbeans = getBoolean(props, "registerMbeans", false);
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
        hc.setDriverClassName("org.sqlite.JDBC");
        hc.setMaximumPoolSize(maxPoolSize);
        hc.setMinimumIdle(minIdle);
        hc.setPoolName(poolName);
        hc.setRegisterMbeans(registerMbeans);

        hc.addDataSourceProperty("busy_timeout", busyTimeoutMs);
        hc.addDataSourceProperty("foreign_keys", foreignKeys);

        HikariDataSource oldDelegate = delegate;
        if (registerMbeans || oldDelegate != null && oldDelegate.isRegisterMbeans()) {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            // Coordinate our components across database types and class loaders.
            synchronized (server) {
                replaceDelegate(hc, oldDelegate, server);
            }
        } else {
            replaceDelegate(hc, oldDelegate, null);
        }
    }

    private void replaceDelegate(HikariConfig config, HikariDataSource previous, MBeanServer server) {
        ObjectName[] names = null;
        if (config.isRegisterMbeans()) {
            // Closing the previous pool would unregister a same-name replacement,
            // even if someone had manually removed the previous pool's MBeans.
            if (previous != null && previous.isRegisterMbeans()
                    && config.getPoolName().equals(previous.getPoolName())) {
                throw new IllegalStateException("JMX poolName is already active: " + config.getPoolName()
                    + "; change poolName when replacing an enabled pool, or restart the component");
            }
            names = availableJmxNames(config.getPoolName(), server);
        }
        HikariDataSource replacement = new HikariDataSource(config);
        try {
            if (names != null && (!server.isRegistered(names[0]) || !server.isRegistered(names[1]))) {
                throw new IllegalStateException("Hikari could not register JMX MBeans for poolName: " + config.getPoolName());
            }
        } catch (RuntimeException | Error failure) {
            try { replacement.close(); }
            catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        delegate = replacement;
        closeQuietly(previous);
    }

    private static ObjectName[] availableJmxNames(String poolName, MBeanServer server) {
        boolean modern = "true".equals(System.getProperty("hikaricp.jmx.register2.0"));
        ObjectName[] names = new ObjectName[2];
        String[] types = {"PoolConfig", "Pool"};
        for (int i = 0; i < names.length; i++) {
            try {
                names[i] = new ObjectName("com.zaxxer.hikari:type=" + types[i]
                    + (modern ? ",name=" + poolName : " (" + poolName + ")"));
            } catch (MalformedObjectNameException invalid) {
                throw new IllegalArgumentException("Invalid JMX poolName: " + poolName, invalid);
            }
            if (names[i].isPattern()) throw new IllegalArgumentException("JMX poolName must not be a pattern: " + poolName);
            if (server.isRegistered(names[i])) {
                throw new IllegalStateException("JMX poolName is already registered: " + poolName
                    + "; choose a unique poolName");
            }
        }
        return names;
    }

    private synchronized void closeDelegate() {
        HikariDataSource ds = delegate;
        if (ds != null && ds.isRegisterMbeans()) {
            synchronized (ManagementFactory.getPlatformMBeanServer()) {
                delegate = null;
                closeQuietly(ds);
            }
        } else {
            delegate = null;
            closeQuietly(ds);
        }
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
