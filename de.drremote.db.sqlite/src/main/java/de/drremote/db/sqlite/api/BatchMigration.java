package de.drremote.db.sqlite.api;

import java.util.List;

/**
 * A single versioned migration made up of multiple explicitly separated SQL statements.
 * All statements are executed in one transaction and recorded as one migration version.
 */
public interface BatchMigration extends Migration {

    List<String> getStatements();

    @Override
    default String getSql() {
        return String.join(";\n", getStatements());
    }
}
