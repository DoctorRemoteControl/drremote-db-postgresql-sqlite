package de.drremote.db.duckdb.impl.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private final String name = "drremote-duckdb-test-" + UUID.randomUUID();

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

    @Test void duplicateActivationCannotTakeAnotherPoolsMBeans() throws Exception {
        HikariDataSourceComponent owner = new HikariDataSourceComponent();
        try {
            owner.activate(properties(name, true));
            assertThrows(IllegalStateException.class, () -> component.activate(properties(name, true)));
            component.deactivate();
            assertTrue(server.isRegistered(pool(name)));
            assertTrue(server.isRegistered(config(name)));
            assertConnectionWorks(owner);
        } finally { owner.deactivate(); }
    }

    @Test void duplicateReplacementPreservesBothIndependentPools() throws Exception {
        HikariDataSourceComponent owner = new HikariDataSourceComponent();
        String occupied = name + "-owner";
        try {
            owner.activate(properties(occupied, true));
            component.activate(properties(name, true));
            assertThrows(IllegalStateException.class, () -> component.modified(properties(occupied, true)));
            assertTrue(server.isRegistered(pool(name)));
            assertTrue(server.isRegistered(pool(occupied)));
            assertConnectionWorks();
            assertConnectionWorks(owner);
            component.deactivate();
            assertTrue(server.isRegistered(pool(occupied)));
            assertConnectionWorks(owner);
        } finally { owner.deactivate(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid,name", "invalid*", "invalid?"})
    void invalidOrPatternJmxNamePreservesTheRunningPool(String invalid) throws Exception {
        component.activate(properties(name, true));
        assertThrows(IllegalArgumentException.class, () -> component.modified(properties(invalid, true)));
        assertEquals(name, component.unwrap(HikariDataSource.class).getPoolName());
        assertTrue(server.isRegistered(pool(name)));
        assertTrue(server.isRegistered(config(name)));
        assertConnectionWorks();
    }

    @Test void alternateHikariNamingConventionUsesTheSameValidation() throws Exception {
        String previous = System.getProperty("hikaricp.jmx.register2.0");
        HikariDataSourceComponent other = new HikariDataSourceComponent();
        System.setProperty("hikaricp.jmx.register2.0", "true");
        try {
            component.activate(properties(name, true));
            assertTrue(server.isRegistered(pool(name)));
            assertThrows(IllegalStateException.class, () -> other.activate(properties(name, true)));
            assertThrows(IllegalArgumentException.class, () -> component.modified(properties("invalid*", true)));
            assertConnectionWorks();
        } finally {
            other.deactivate();
            component.deactivate();
            if (previous == null) System.clearProperty("hikaricp.jmx.register2.0");
            else System.setProperty("hikaricp.jmx.register2.0", previous);
        }
    }

    @Test void concurrentActivationsCannotClaimTheSameJmxName() throws Exception {
        HikariDataSourceComponent other = new HikariDataSourceComponent();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> first = () -> activateAfter(start, component);
            java.util.concurrent.Callable<Boolean> second = () -> activateAfter(start, other);
            var a = executor.submit(first);
            var b = executor.submit(second);
            start.countDown();
            boolean aStarted = a.get(15, java.util.concurrent.TimeUnit.SECONDS);
            boolean bStarted = b.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(aStarted, bStarted, "Exactly one pool may own the name");
            HikariDataSourceComponent winner = aStarted ? component : other;
            HikariDataSourceComponent loser = aStarted ? other : component;
            loser.deactivate();
            assertTrue(server.isRegistered(pool(name)));
            assertConnectionWorks(winner);
        } finally {
            executor.shutdownNow();
            other.deactivate();
        }
    }

    private boolean activateAfter(java.util.concurrent.CountDownLatch start, HikariDataSourceComponent target) throws Exception {
        start.await();
        try {
            target.activate(properties(name, true));
            return true;
        } catch (IllegalStateException conflict) {
            assertTrue(conflict.getMessage().contains("poolName"));
            return false;
        }
    }

    private Map<String, Object> properties(String poolName, Object register) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jdbcUrl", "jdbc:duckdb:" + directory.resolve("monitoring.db"));
        properties.put("poolName", poolName);
        properties.put("maxPoolSize", 1);
        properties.put("minIdle", 1);
        if (register != null) properties.put("registerMbeans", register);
        return properties;
    }

    private void assertConnectionWorks() throws Exception { assertConnectionWorks(component); }

    private void assertConnectionWorks(HikariDataSourceComponent target) throws Exception {
        try (var connection = target.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT 1")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    private ObjectName pool(String name) throws Exception {
        return new ObjectName("com.zaxxer.hikari:type=Pool" + ("true".equals(System.getProperty("hikaricp.jmx.register2.0")) ? ",name=" + name : " (" + name + ")"));
    }

    private ObjectName config(String name) throws Exception {
        return new ObjectName("com.zaxxer.hikari:type=PoolConfig" + ("true".equals(System.getProperty("hikaricp.jmx.register2.0")) ? ",name=" + name : " (" + name + ")"));
    }
}
