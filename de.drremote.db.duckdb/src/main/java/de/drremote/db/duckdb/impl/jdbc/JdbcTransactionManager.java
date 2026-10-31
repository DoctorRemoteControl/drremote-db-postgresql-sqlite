package de.drremote.db.duckdb.impl.jdbc;

import de.drremote.db.duckdb.api.DbCoreException;
import de.drremote.db.duckdb.api.TransactionCallback;
import de.drremote.db.duckdb.api.TransactionManager;
import org.osgi.service.component.annotations.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.logging.Logger;

@Component(service = TransactionManager.class)
public final class JdbcTransactionManager implements TransactionManager {

    private static final Logger LOG = Logger.getLogger(JdbcTransactionManager.class.getName());

    private DataSource dataSource;

    @Reference(target = "(drremote.db.kind=duckdb)")
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
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            JdbcDbHandle handle = new JdbcDbHandle(conn);
            try {
                T result = callback.doInTransaction(handle);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new DbCoreException("Transaction failed", e);
        }
    }
}
