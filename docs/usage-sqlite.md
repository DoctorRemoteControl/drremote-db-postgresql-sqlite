# SQLite usage

The service interfaces are in `de.drremote.db.sqlite.api`. Install/configure the bundle first; the [notes client](../examples/notes-client) is a complete, buildable OSGi example.

## Queries and transactions

Reference `TransactionManager` from an OSGi Declarative Services component:

```java
@Reference
private TransactionManager transactions;

public List<String> names() {
    return transactions.inTransaction(db -> db.getQueryRunner().query(
        "SELECT text FROM notes WHERE id > ? ORDER BY id",
        row -> row.getString("text"), 0));
}
```

Use `update(sql, parameters...)` for changes, `query(...)` for a list, and `queryOne(...)` for at most one result (`null` when absent; an exception for multiple results). Bind values using `?` parameters. Table/schema identifiers are part of the SQL text and are not value parameters.

The outer transaction commits on success and rolls back on failure. Nested calls on the **same manager and thread** share the connection and transaction. An inner failure marks the transaction rollback-only even if the caller catches it. Other threads/manager instances do not inherit the transaction. Do not retain the callback's connection or handle after it returns; closing a `DbHandle` does not close the manager's connection.

## Automatic migrations and readiness

Publish an OSGi `PluginMigrationDescriptor` service with a stable plugin name and ordered version numbers. [NotesMigrations.java](../examples/notes-client/src/main/java/example/notes/NotesMigrations.java) creates a table in migration 1. The registry applies descriptors and records completed versions in `core_schema_migrations`.

Wait for the plugin's readiness service before accessing its tables:

```java
@Reference(target = "(drremote.db.plugin=notes)")
private DatabaseReady ready;
```

The marker is published only after the descriptor migrates successfully. [NotesRepository.java](../examples/notes-client/src/main/java/example/notes/NotesRepository.java) demonstrates this alongside a transaction reference.

Regular migrations contain a single SQL statement. The current statement guard rejects embedded semicolons, including those inside SQL literals/comments; a final semicolon is allowed. Do not feed SQL scripts to this interface.

## Atomic batches

Implement `BatchMigration` when a version needs several explicit statements:

```java
new BatchMigration() {
    public int getVersion() { return 2; }
    public String getDescription() { return "Add note index"; }
    public List<String> getStatements() {
        return List.of(
            "ALTER TABLE notes ADD COLUMN category TEXT",
            "CREATE INDEX notes_category ON notes(category)"
        );
    }
};
```

All statements in that version commit or roll back together and create one tracking row. Each list entry must still be a single statement.

## Baselines and upgrades

`PluginMigrationDescriptor.getBaseline()` may return a `MigrationBaseline` representing the current schema through a particular version. It is used only for a plugin with no migration history; existing databases continue through incremental migrations. Keep the incremental history and add a new migration for every subsequent change. Baseline state is recorded in `core_schema_baselines`.

Do not edit applied migration SQL. Replay skips matching versions, and changed SQL is reported as migration drift when the previous SQL was recorded. The test suite covers replay, atomic rollback, baseline adoption, incremental upgrades, and drift detection.
