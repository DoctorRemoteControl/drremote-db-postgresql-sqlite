package de.drremote.db.sqlite.api;

import java.util.List;

public interface PluginMigrationDescriptor {

    String getPluginName();

    List<Migration> getMigrations();

    /**
     * Optional current-schema snapshot for databases without any history for this plugin.
     * Existing installations always continue with {@link #getMigrations()}.
     */
    default MigrationBaseline getBaseline() {
        return null;
    }
}
