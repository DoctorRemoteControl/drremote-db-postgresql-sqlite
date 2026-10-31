# DuckDB usage

The interfaces are in `de.drremote.db.duckdb.api`. [Configure](configuration-duckdb.md) and install the DuckDB feature first.

## JDBC work

Reference `TransactionManager` from a Declarative Services component and run work inside its callback:

```java
@Reference
private TransactionManager transactions;

public long itemCount() {
    return transactions.inTransaction(db -> db.getQueryRunner().queryOne(
        "SELECT COUNT(*) FROM items", row -> row.getLong(1)));
}
```

`query(...)`, `queryOne(...)`, and `update(...)` support `?` value parameters. `queryOne` returns `null` for no result and rejects multiple rows. The callback's work commits on success and rolls back on an exception.

The DuckDB implementation is simpler than the PostgreSQL/SQLite layers: **do not nest transaction callbacks**. Its single-connection pool cannot serve a second connection while the outer callback is holding the first. Do not retain the connection beyond the callback or share writable files between application instances.

## Migrations

Implement `PluginMigrationDescriptor` using `getPluginName()` and `getMigrations()`. Each `Migration` supplies its version, description, and a single SQL statement. Reference `MigrationService` and call `migratePlugin(descriptor)` explicitly before accessing the plugin's tables.

DuckDB does not currently automatically register descriptors or publish `DatabaseReady` markers. Your component controls migration-before-use ordering.

Migrations are sorted by version and recorded in `core_schema_migrations`. Replaying an already recorded version skips it. Each version is transactional; several versions do not form one transaction. Do not change already-applied migration SQL.

The current statement guard rejects embedded semicolons, including those in literals/comments; a trailing semicolon is allowed. Multi-statement scripts, SQLite-style batch/baseline migrations, and drift checking are not part of this DuckDB implementation.

Real temporary-database tests cover parameterized JDBC access, transaction rollback/recovery, version ordering, migration replay, failed-version tracking, and the statement guard.
