package de.drremote.db.postgresql.api;

/**
 * Marker + lifecycle for the PostgreSQL core.
 * Referencing this service guarantees the PostgreSQL core is ACTIVE.
 */
public interface DbCore {

    boolean isReady();
}
