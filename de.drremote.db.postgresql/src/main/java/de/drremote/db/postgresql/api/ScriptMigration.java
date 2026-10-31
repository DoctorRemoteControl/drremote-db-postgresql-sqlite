/* === de.drremote.db.postgresql/src/main/java/de/drremote/db/postgresql/api/ScriptMigration.java === */
package de.drremote.db.postgresql.api;

import java.net.URL;

/**
 * Migration backed by an external SQL script executed via psql.
 *
 * getSql() is intentionally unsupported here.
 */
public interface ScriptMigration extends Migration {

    /**
     * URL to the SQL script resource.
     */
    URL getScriptUrl();

    /**
     * Whether psql should execute the script with --single-transaction.
     *
     * Override and return false for PostgreSQL commands that are not allowed
     * inside an explicit transaction block.
     */
    default boolean isSingleTransaction() {
        return true;
    }

    /**
     * Script migrations are NOT executed via JDBC SQL strings.
     * This method must never be used.
     */
    @Override
    default String getSql() {
        throw new UnsupportedOperationException(
            "ScriptMigration does not support getSql(); use getScriptUrl() instead"
        );
    }
}
