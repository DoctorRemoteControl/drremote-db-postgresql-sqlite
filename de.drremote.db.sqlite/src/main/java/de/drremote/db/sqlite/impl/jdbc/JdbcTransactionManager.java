package de.drremote.db.sqlite.impl.jdbc;

import de.drremote.db.sqlite.api.DbCoreException;
import de.drremote.db.sqlite.api.TransactionCallback;
import de.drremote.db.sqlite.api.TransactionManager;
import org.osgi.service.component.annotations.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.logging.Logger;

@Component(service = TransactionManager.class)
public final class JdbcTransactionManager implements TransactionManager {

    private static final Logger LOG = Logger.getLogger(JdbcTransactionManager.class.getName());
    private final ThreadLocal<TransactionState> current = new ThreadLocal<>();
    private DataSource dataSource;

    @Reference(target = "(drremote.db.kind=sqlite)")
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        LOG.info("DataSource bound");
    }

    @Activate
    void activate() {
        LOG.info("TransactionManager activated");
    }

    @Override
    public <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException {
        Objects.requireNonNull(callback, "callback");
        TransactionState existing = current.get();
        if (existing != null) return join(existing, callback);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            TransactionState state = new TransactionState(new JdbcDbHandle(conn));
            current.set(state);
            try {
                T result = callback.doInTransaction(state.handle);
                if (state.failure != null) {
                    throw new DbCoreException("Transaction marked rollback-only by nested failure", state.failure);
                }
                conn.commit();
                return result;
            } catch (Exception | Error failure) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                current.remove();
            }
        } catch (Exception failure) {
            throw new DbCoreException("Transaction failed", failure);
        }
    }

    private <T> T join(TransactionState state, TransactionCallback<T> callback) {
        if (state.failure != null) {
            throw new DbCoreException("Transaction is already rollback-only", state.failure);
        }
        try {
            return callback.doInTransaction(state.handle);
        } catch (Exception | Error failure) {
            if (state.failure == null) state.failure = failure;
            if (failure instanceof Error error) throw error;
            throw new DbCoreException("Nested transaction failed", failure);
        }
    }

    private static final class TransactionState {
        private final JdbcDbHandle handle;
        private Throwable failure;

        private TransactionState(JdbcDbHandle handle) {
            this.handle = handle;
        }
    }
}
