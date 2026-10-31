package de.drremote.db.duckdb.api;

public interface Migration {

    int getVersion();

    String getDescription();

    String getSql();
}
