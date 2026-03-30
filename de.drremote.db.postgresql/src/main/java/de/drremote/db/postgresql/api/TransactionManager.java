package de.drremote.db.postgresql.api;

public interface TransactionManager {

    <T> T inTransaction(TransactionCallback<T> callback) throws DbCoreException;
}
