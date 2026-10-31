package de.drremote.db.duckdb.api;

import java.util.List;

public interface PluginMigrationDescriptor {

    String getPluginName();

    List<Migration> getMigrations();
}
