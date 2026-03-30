package de.drremote.db.sqlite.api;

import java.util.List;

public interface PluginMigrationDescriptor {

    String getPluginName();

    List<Migration> getMigrations();
}