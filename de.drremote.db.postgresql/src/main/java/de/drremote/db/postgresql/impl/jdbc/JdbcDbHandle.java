package de.drremote.db.postgresql.impl.jdbc;

import de.drremote.db.postgresql.api.DbCoreException;
import de.drremote.db.postgresql.api.DbHandle;
import de.drremote.db.postgresql.api.QueryRunner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
    public void setSearchPath(String schema) {
        if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new DbCoreException("Invalid schema name: " + schema);
        }
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO " + schema + ", core, public");
        } catch (SQLException e) {
            throw new DbCoreException(
                "Failed to set search_path to schema '" + schema + "'", e
            );
        }
    }

    @Override
    public void close() {
        // no-op: lifecycle is managed by JdbcTransactionManager
    }
}
