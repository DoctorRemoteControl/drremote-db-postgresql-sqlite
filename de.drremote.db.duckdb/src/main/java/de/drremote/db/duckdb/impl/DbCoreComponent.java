package de.drremote.db.duckdb.impl;

import de.drremote.db.duckdb.api.DbCore;
import de.drremote.db.duckdb.api.TransactionManager;
import org.osgi.service.component.annotations.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.logging.Logger;

@Component(
    service = DbCore.class,
    immediate = true
)
public final class DbCoreComponent implements DbCore {

    private static final Logger LOG = Logger.getLogger(DbCoreComponent.class.getName());

    private DataSource dataSource;
    private TransactionManager transactionManager;

    private volatile boolean ready = false;

    @Reference(target = "(drremote.db.kind=duckdb)")
    void bindDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Reference
    void bindTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Activate
    void activate() {
        if (dataSource == null || transactionManager == null) {
            throw new IllegalStateException("DbCore requires DataSource and TransactionManager");
        }

        try (Connection conn = dataSource.getConnection();
            Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
        } catch (Exception e) {
            throw new IllegalStateException("Database connectivity check failed", e);
        }

        ready = true;
        LOG.info("Core activated");
    }

    @Deactivate
    void deactivate() {
        ready = false;
        LOG.info("Core deactivated");
    }

    @Override
    public boolean isReady() {
        return ready;
    }
}
