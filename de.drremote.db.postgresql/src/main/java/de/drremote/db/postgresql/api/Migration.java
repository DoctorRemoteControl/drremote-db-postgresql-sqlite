package de.drremote.db.postgresql.api;

public interface Migration {

    int getVersion();

    String getDescription();

    String getSql();
}
