package de.drremote.db.duckdb.api;

@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction(DbHandle handle) throws Exception;
}
