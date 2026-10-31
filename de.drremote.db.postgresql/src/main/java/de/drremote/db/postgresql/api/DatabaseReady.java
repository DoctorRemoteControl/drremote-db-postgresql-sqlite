package de.drremote.db.postgresql.api;

/**
 * Marker service published after all migrations of a PostgreSQL plugin succeeded.
 */
public interface DatabaseReady {

    String PROPERTY_KIND = "drremote.db.kind";
    String PROPERTY_PLUGIN = "drremote.db.plugin";
    String PROPERTY_SCHEMA = "drremote.db.schema";

    String getDatabaseKind();

    String getPluginName();

    String getSchemaName();
}
