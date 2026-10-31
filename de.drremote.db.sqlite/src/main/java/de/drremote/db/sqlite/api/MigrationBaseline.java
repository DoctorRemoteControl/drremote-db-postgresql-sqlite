package de.drremote.db.sqlite.api;

import java.util.List;

/**
 * Current schema snapshot used only when a plugin has no migration history yet.
 * The version identifies the latest incremental migration represented by the snapshot.
 */
public interface MigrationBaseline {

    int getVersion();

    String getDescription();

    List<String> getStatements();
}
