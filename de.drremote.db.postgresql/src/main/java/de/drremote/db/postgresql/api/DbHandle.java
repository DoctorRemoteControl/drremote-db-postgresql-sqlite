package de.drremote.db.postgresql.api;

import java.sql.Connection;

public interface DbHandle extends AutoCloseable {

    Connection getConnection();

    QueryRunner getQueryRunner();

    /**
     * Sets the PostgreSQL search_path for this transaction.
     * Order: plugin schema, core, public
     */
    void setSearchPath(String schema);

    @Override
    void close();
}
