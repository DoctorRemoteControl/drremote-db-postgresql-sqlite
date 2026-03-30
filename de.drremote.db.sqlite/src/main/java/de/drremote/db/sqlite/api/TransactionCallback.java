package de.drremote.db.sqlite.api;

@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction(DbHandle handle) throws Exception;
}