package de.drremote.db.sqlite.api;

import java.sql.Connection;

public interface DbHandle extends AutoCloseable {

    Connection getConnection();

    QueryRunner getQueryRunner();

    @Override
    void close();
}