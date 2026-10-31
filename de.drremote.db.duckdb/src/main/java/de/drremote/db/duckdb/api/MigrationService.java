package de.drremote.db.duckdb.api;

public interface MigrationService {
    void migratePlugin(PluginMigrationDescriptor descriptor) throws DbCoreException;
}
