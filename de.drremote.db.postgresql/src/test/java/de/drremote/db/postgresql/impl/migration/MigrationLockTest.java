package de.drremote.db.postgresql.impl.migration;

import de.drremote.db.postgresql.api.*;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MigrationLockTest {
    @Test
    void failedAdvisoryLockAcquisitionClosesBorrowedConnection() {
        AtomicBoolean borrowed = new AtomicBoolean();
        SQLException lockFailure = new SQLException("lock timeout");
        Connection connection = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) throw lockFailure;
                if (method.getName().equals("close")) { borrowed.set(false); return null; }
                throw new AssertionError(method.getName());
            });
        DataSource source = (DataSource) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    assertTrue(borrowed.compareAndSet(false, true), "Pool capacity was leaked");
                    return connection;
                }
                throw new AssertionError(method.getName());
            });
        MigrationServiceImpl service = new MigrationServiceImpl();
        service.bindDataSource(source);
        service.bindTransactionManager(new TransactionManager() {
            public <T> T inTransaction(TransactionCallback<T> callback) { throw new AssertionError("Unexpected transaction"); }
        });
        PluginMigrationDescriptor descriptor = new PluginMigrationDescriptor() {
            public String getPluginName() { return "lock-test"; }
            public String getSchemaName() { return "lock_test"; }
            public List<Migration> getMigrations() {
                return List.of(new Migration() {
                    public int getVersion() { return 1; }
                    public String getDescription() { return "unused"; }
                    public String getSql() { return "SELECT 1"; }
                });
            }
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> service.migratePlugin(descriptor));
        assertSame(lockFailure, failure.getCause());
        assertFalse(borrowed.get(), "Failed lock acquisition must release its connection");
        assertThrows(IllegalStateException.class, () -> service.migratePlugin(descriptor));
        assertFalse(borrowed.get());
    }
}
