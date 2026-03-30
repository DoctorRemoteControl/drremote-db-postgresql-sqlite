package de.drremote.db.sqlite.api;

public interface Migration {

    int getVersion();

    String getDescription();

    String getSql();
}