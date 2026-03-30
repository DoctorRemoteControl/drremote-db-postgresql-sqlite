package de.drremote.db.postgresql.api;

import java.util.List;

public interface PluginMigrationDescriptor {

    String getPluginName();

    String getSchemaName();

    List<Migration> getMigrations();
}
