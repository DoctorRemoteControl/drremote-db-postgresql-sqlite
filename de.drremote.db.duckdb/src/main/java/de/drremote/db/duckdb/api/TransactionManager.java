package de.drremote.db.duckdb.api;

public interface TransactionManager {
    <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException;
}
