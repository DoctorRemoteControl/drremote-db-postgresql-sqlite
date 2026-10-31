package de.drremote.db.postgresql.impl.jdbc;

import de.drremote.db.postgresql.api.TransactionManager;
import javax.sql.DataSource;

public final class PostgresTestTransactions {
    private PostgresTestTransactions() {}

    public static TransactionManager using(DataSource dataSource) {
        JdbcTransactionManager manager = new JdbcTransactionManager();
        manager.bindDataSource(dataSource);
        return manager;
    }
}
