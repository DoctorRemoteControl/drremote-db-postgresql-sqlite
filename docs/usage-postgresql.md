# PostgreSQL usage

The service interfaces are in `de.drremote.db.postgresql.api`. [Configure a running database](configuration-postgresql.md) before installing the feature.

## Queries and transactions

Reference `TransactionManager` from an OSGi Declarative Services component:

```java
@Reference
private TransactionManager transactions;

public List<String> names() {
    return transactions.inTransaction(db -> db.getQueryRunner().query(
        "SELECT name FROM app.customer WHERE id > ? ORDER BY id",
        row -> row.getString("name"), 0));
}
```

`query(...)` returns a list, `queryOne(...)` returns a single result or `null` (and rejects multiple results), and `update(...)` returns the affected-row count. Bind values with `?`; identifiers must be part of the SQL text.

An outer callback commits once on success and rolls back on failure. Nested calls on the same manager/thread join that transaction. An inner failure marks it rollback-only even when caught. Transaction context is not propagated across threads or manager instances. Do not retain connections/handles beyond the callback.

## Plugin migrations

Publish an OSGi `PluginMigrationDescriptor` service implementing:

```java
String getPluginName();
String getSchemaName();
List<Migration> getMigrations();
```

Use a stable plugin name and schema name and increasing version numbers. A `Migration` supplies `getVersion()`, `getDescription()`, and `getSql()`. The migration service creates the plugin schema, applies JDBC migrations, and records versions centrally in `core.schema_migrations`.

Regular JDBC migration text is passed to the JDBC driver as one execution. Prefer one SQL statement per version, and use script migrations for actual `psql` scripts. Do not mix unmanaged schema modifications into the recorded migration history.

A consumer can wait for successful plugin migration through:

```java
@Reference(target = "(drremote.db.plugin=my-plugin)")
private DatabaseReady ready;
```

`DbCore` indicates the core service is active. `DatabaseReady` identifies successful migration for a specific plugin; the two services serve different purposes.

## Script migrations

`psql` is an intentional, supported execution path for complex PostgreSQL
migrations. Keep it installed on the runtime host when plugins use
`ScriptMigration`; this support is not replaced by the JDBC query helper.

Implement `ScriptMigration` when a migration needs a resource URL, `psql` commands, or a SQL script:

```java
@Override
public URL getScriptUrl() {
    return getClass().getResource("/migrations/V2__customer.sql");
}
```

The resource must exist in your client bundle. The runner loads the script into a temporary file, sets the plugin search path in the same `psql` session, stops on SQL errors, ignores `.psqlrc`, and never prompts for a password.

`ScriptMigration.isSingleTransaction()` defaults to `true`. Override it to `false` only when the script contains commands that PostgreSQL forbids in a transaction. That opt-out removes whole-script atomicity; design the script so a partial failure can be recovered safely.

The configured `psqlConnectTimeoutSeconds` limits connection establishment. It does not impose a whole-script execution timeout. JDBC migrations and an external `psql` process do not share one JDBC transaction.

### Choosing JDBC or psql

PostgreSQL functions and `DO $$ ... $$` blocks are SQL and can also execute over
JDBC when sent as complete statements. Internal semicolons are not a reason to
split their bodies. In contrast, `\copy`, `\set`, `\if`, and `\gexec` are client
meta-commands interpreted by psql; passing them to a JDBC statement does not
provide equivalent behavior. Use `ScriptMigration` for those scripts and for
existing psql-oriented migration resources.

For example, a script resource can contain both procedural SQL and psql commands:

```sql
CREATE TABLE app_labels (id INTEGER PRIMARY KEY, label TEXT NOT NULL);

DO $migration$
BEGIN
    INSERT INTO app_labels VALUES (1, 'text; with an internal semicolon');
END;
$migration$;

\copy app_labels (id, label) FROM stdin WITH (FORMAT csv)
2,imported label
\.
```

The script runs with the plugin's search path. Keep migration versions ordered
and do not add your own `BEGIN`/`COMMIT` inside a script that relies on the
default single-transaction wrapper. `CREATE INDEX CONCURRENTLY` is an example
that requires `isSingleTransaction()` to return false; a failed nontransactional
script can leave partial work and needs an application-specific recovery plan.
The migration's tracking row is written only after successful script execution.

Scripts are loaded from their resource URL into a temporary file. Additional
files referenced by `\i`, `\ir`, or file-based `\copy` must exist at the paths
psql resolves on the runtime host; sibling bundle resources are not extracted
automatically. `COPY ... FROM stdin` input embedded in the script is supported.

The [real integration fixture](../de.drremote.db.postgresql/src/test/resources/migrations/complex-psql.sql)
exercises procedural SQL, meta-commands, and COPY input together. Its `${table}`
placeholder is substituted by the test, not by the production migration runner.

## Validation

The default Maven suite exercises nested transaction coordination, rollback/error paths, JDBC-to-psql URL conversion, and script command construction/execution with a temporary fake executable. It does not start a live PostgreSQL server. With `DRREMOTE_TEST_POSTGRES_URL` set, additional integration tests require a real `psql` executable and check schema placement, replay, a one-connection pool, mixed JDBC/psql migrations, complex procedural/meta-command scripts, transactional rollback/history, and concurrent index creation with an explicit transaction opt-out. CI supplies PostgreSQL 17; see [contributing](../CONTRIBUTING.md) for local setup. Validate your own migrations against the PostgreSQL versions you support.
