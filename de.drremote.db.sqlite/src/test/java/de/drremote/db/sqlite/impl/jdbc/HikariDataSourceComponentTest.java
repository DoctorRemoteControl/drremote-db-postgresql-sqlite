package de.drremote.db.sqlite.impl.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;


class HikariDataSourceComponentTest {
    @TempDir Path directory;
    private final HikariDataSourceComponent component = new HikariDataSourceComponent();
    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final String name = "drremote-sqlite-test-" + UUID.randomUUID();

    @AfterEach void stop() { component.deactivate(); }

    @Test void monitoringIsDisabledByDefault() throws Exception {
        component.activate(properties(name, null));
        assertFalse(component.unwrap(HikariDataSource.class).isRegisterMbeans());
        assertFalse(server.isRegistered(pool(name)));
        assertConnectionWorks();
    }

    @Test void enabledMonitoringReportsBorrowedConnectionsAndCleansUpOnStop() throws Exception {
        component.activate(properties(name, "true"));
        assertTrue(component.unwrap(HikariDataSource.class).isRegisterMbeans());
        assertTrue(server.isRegistered(pool(name)));
        assertTrue(server.isRegistered(config(name)));
        try (var connection = component.getConnection()) {
            assertEquals(1, ((Number) server.getAttribute(pool(name), "ActiveConnections")).intValue());
            assertEquals(0, ((Number) server.getAttribute(pool(name), "ThreadsAwaitingConnection")).intValue());
        }
        component.deactivate();
        assertFalse(server.isRegistered(pool(name)));
        assertFalse(server.isRegistered(config(name)));
    }

    @Test void disabledMonitoringCanBeEnabledByReconfiguration() throws Exception {
        component.activate(properties(name, false));
        component.modified(properties(name, true));
        assertTrue(server.isRegistered(pool(name)));
        assertConnectionWorks();
    }

    @Test void enabledMonitoringCanBeDisabledByReconfiguration() throws Exception {
        component.activate(properties(name, true));
        assertTrue(server.isRegistered(pool(name)));
        component.modified(properties(name, false));
        assertFalse(server.isRegistered(pool(name)));
        assertFalse(server.isRegistered(config(name)));
        assertConnectionWorks();
    }

    @Test void conflictingEnabledPoolNameIsRejectedWithoutLosingTheRunningPool() throws Exception {
        component.activate(properties(name, true));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> component.modified(properties(name, true)));
        assertTrue(failure.getMessage().contains("poolName"));
        assertTrue(server.isRegistered(pool(name)));
        assertConnectionWorks();
    }

    @Test void enabledPoolCanBeReplacedUsingADistinctName() throws Exception {
        component.activate(properties(name, true));
        component.modified(properties(name + "-replacement", true));
        assertFalse(server.isRegistered(pool(name)));
        assertFalse(server.isRegistered(config(name)));
        assertTrue(server.isRegistered(pool(name + "-replacement")));
        assertConnectionWorks();
        component.deactivate();
        assertFalse(server.isRegistered(pool(name + "-replacement")));
        assertFalse(server.isRegistered(config(name + "-replacement")));
    }

    @Test void invalidConfigurationPreservesTheRunningPool() throws Exception {
        component.activate(properties(name, true));
        Map<String, Object> invalid = properties(name + "-invalid", true);
        invalid.put("maxPoolSize", 0);
        assertThrows(RuntimeException.class, () -> component.modified(invalid));
        assertTrue(server.isRegistered(pool(name)));
        assertConnectionWorks();
    }

    private Map<String, Object> properties(String poolName, Object register) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jdbcUrl", "jdbc:sqlite:" + directory.resolve("monitoring.db"));
        properties.put("poolName", poolName);
        properties.put("maxPoolSize", 1);
        properties.put("minIdle", 1);
        if (register != null) properties.put("registerMbeans", register);
        return properties;
    }

    private void assertConnectionWorks() throws Exception {
        try (var connection = component.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT 1")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    private ObjectName pool(String name) throws Exception {
        return new ObjectName("com.zaxxer.hikari:type=Pool (" + name + ")");
    }

    private ObjectName config(String name) throws Exception {
        return new ObjectName("com.zaxxer.hikari:type=PoolConfig (" + name + ")");
    }
}
