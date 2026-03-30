package de.drremote.db.postgresql.api;

@FunctionalInterface
public interface TransactionCallback<T> {

    T doInTransaction(DbHandle handle) throws Exception;
}
