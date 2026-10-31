package de.drremote.db.postgresql.impl.jdbc;

import de.drremote.db.postgresql.api.DbCoreException;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcTransactionManagerTest {
    private final List<ConnectionState> connections = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final JdbcTransactionManager tx = manager();

    @Test void nestedCallsUseOneConnectionAndCommitOnce() {
        assertEquals("done", tx.inTransaction(outer -> {
            tx.inTransaction(inner -> {
                assertSame(outer.getConnection(), inner.getConnection());
                return null;
            });
            assertEquals(0, connections.get(0).commits);
            assertEquals(0, connections.get(0).closes);
            return "done";
        }));
        assertEquals(1, connections.size());
        assertEquals(1, connections.get(0).commits);
        assertEquals(0, connections.get(0).rollbacks);
        assertEquals(1, connections.get(0).closes);
    }

    @Test void caughtInnerFailureRollsBackOuterAndCleansThreadState() {
        assertThrows(DbCoreException.class, () -> tx.inTransaction(outer -> {
            assertThrows(DbCoreException.class, () -> tx.inTransaction(inner -> {
                throw new SQLException("failed write");
            }));
            return null;
        }));
        assertEquals(0, connections.get(0).commits);
        assertEquals(1, connections.get(0).rollbacks);
        tx.inTransaction(handle -> null);
        assertEquals(2, connections.size());
        assertEquals(1, connections.get(1).commits);
    }

    @Test void errorAlsoRollsBackAndCleansThreadState() {
        AssertionError failure = new AssertionError("stop");
        assertSame(failure, assertThrows(AssertionError.class, () -> tx.inTransaction(outer ->
                tx.inTransaction(inner -> { throw failure; }))));
        assertEquals(1, connections.get(0).rollbacks);
        assertEquals(1, connections.get(0).closes);
        tx.inTransaction(handle -> null);
        assertEquals(2, connections.size());
    }

    @Test void rollbackFailureDoesNotHideOriginalFailure() {
        SQLException original = new SQLException("write failed");
        DbCoreException failure = assertThrows(DbCoreException.class, () -> tx.inTransaction(handle -> {
            connections.get(0).failRollback = true;
            throw original;
        }));
        assertSame(original, failure.getCause());
        assertEquals("rollback failed", original.getSuppressed()[0].getMessage());
        assertEquals(1, connections.get(0).closes);
    }

    @Test void commitFailureRollsBackAndReleasesConnection() {
        assertThrows(DbCoreException.class, () -> tx.inTransaction(handle -> {
            connections.get(0).failCommit = true;
            return null;
        }));
        assertEquals(1, connections.get(0).rollbacks);
        assertEquals(1, connections.get(0).closes);
    }

    @Test void concurrentThreadsHaveIndependentTransactions() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Connection> work = () -> tx.inTransaction(outer -> {
            barrier.await(5, TimeUnit.SECONDS);
            return tx.inTransaction(inner -> inner.getConnection());
        });
        try {
            Future<Connection> first = executor.submit(work);
            Future<Connection> second = executor.submit(work);
            assertNotSame(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(2, connections.size());
            assertTrue(connections.stream().allMatch(state -> state.commits == 1 && state.closes == 1));
        } finally { executor.shutdownNow(); }
    }

    @Test void nestedRepositoryRestoresOuterSearchPath() {
        tx.inTransaction(outer -> {
            outer.setSearchPath("iam");
            tx.inTransaction(inner -> { inner.setSearchPath("emailverify"); return null; });
            assertEquals("iam, core, public", connections.get(0).searchPath);
            return null;
        });
    }

    private JdbcTransactionManager manager() {
        JdbcTransactionManager result = new JdbcTransactionManager();
        result.bindDataSource(proxy(DataSource.class, (p, method, args) -> {
            if (!method.getName().equals("getConnection")) throw new UnsupportedOperationException();
            ConnectionState state = new ConnectionState();
            connections.add(state);
            return state.connection();
        }));
        return result;
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class ConnectionState {
        int commits, rollbacks, closes;
        boolean failRollback, failCommit;
        String searchPath = "core, public";

        Connection connection() {
            return proxy(Connection.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "setAutoCommit": return null;
                    case "commit":
                        if (failCommit) throw new SQLException("commit failed");
                        commits++; return null;
                    case "rollback":
                        rollbacks++;
                        if (failRollback) throw new SQLException("rollback failed");
                        return null;
                    case "close": closes++; return null;
                    case "createStatement": return proxy(Statement.class, (sp, sm, sa) -> {
                        if (sm.getName().equals("close")) return null;
                        if (sm.getName().equals("execute")) {
                            searchPath = ((String) sa[0]).substring("SET search_path TO ".length());
                            return false;
                        }
                        if (sm.getName().equals("executeQuery")) {
                            String saved = searchPath;
                            return proxy(ResultSet.class, (rp, rm, ra) -> switch (rm.getName()) {
                                case "next" -> true;
                                case "getString" -> saved;
                                case "close" -> null;
                                default -> throw new UnsupportedOperationException(rm.getName());
                            });
                        }
                        throw new UnsupportedOperationException(sm.getName());
                    });
                    case "prepareStatement": return proxy(PreparedStatement.class, (pp, pm, pa) -> {
                        if (pm.getName().equals("setString")) { searchPath = (String) pa[1]; return null; }
                        if (pm.getName().equals("execute")) return true;
                        if (pm.getName().equals("close")) return null;
                        throw new UnsupportedOperationException(pm.getName());
                    });
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }
    }
}
