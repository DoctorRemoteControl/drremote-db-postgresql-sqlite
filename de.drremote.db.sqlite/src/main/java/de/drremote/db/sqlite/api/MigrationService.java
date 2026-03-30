package de.drremote.db.sqlite.api;

public interface MigrationService {
    void migratePlugin(PluginMigrationDescriptor descriptor) throws DbCoreException;
}