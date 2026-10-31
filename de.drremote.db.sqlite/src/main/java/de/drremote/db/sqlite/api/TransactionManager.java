package de.drremote.db.sqlite.api;

public interface TransactionManager {

    /**
     * Runs work atomically. Nested calls on this manager and thread join the outer
     * transaction; only the outer call commits or rolls back. A nested failure marks
     * the entire transaction rollback-only, even if the caller catches it.
     * Transactions do not propagate to other threads or manager instances.
     */
    <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException;
}
