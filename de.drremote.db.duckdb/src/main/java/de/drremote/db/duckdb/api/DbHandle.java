package de.drremote.db.duckdb.api;

import java.sql.Connection;

public interface DbHandle extends AutoCloseable {

    Connection getConnection();

    QueryRunner getQueryRunner();

    @Override
    void close();
}
