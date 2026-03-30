package de.drremote.db.postgresql.api;

import java.util.List;

public interface MigrationService {

    void migrateCoreSchema(List<Migration> migrations) throws DbCoreException;

    void migratePlugin(PluginMigrationDescriptor descriptor) throws DbCoreException;
}
