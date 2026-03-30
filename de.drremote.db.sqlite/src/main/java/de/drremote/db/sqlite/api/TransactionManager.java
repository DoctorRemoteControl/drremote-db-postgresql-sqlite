package de.drremote.db.sqlite.api;

public interface TransactionManager {
    <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException;
}