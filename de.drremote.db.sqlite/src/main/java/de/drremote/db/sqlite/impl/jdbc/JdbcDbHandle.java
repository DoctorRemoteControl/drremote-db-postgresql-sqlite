package de.drremote.db.sqlite.impl.jdbc;

import de.drremote.db.sqlite.api.DbCoreException;
import de.drremote.db.sqlite.api.DbHandle;
import de.drremote.db.sqlite.api.QueryRunner;

import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcDbHandle implements DbHandle {

    private final Connection connection;
    private final QueryRunner queryRunner;

    public JdbcDbHandle(Connection connection) {
        this.connection = connection;
        this.queryRunner = new JdbcQueryRunner(connection);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public QueryRunner getQueryRunner() {
        return queryRunner;
    }

    @Override
    public void close() {
        // no-op: lifecycle is managed by JdbcTransactionManager
    }
}
