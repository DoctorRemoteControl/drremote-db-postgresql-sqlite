# Changelog

## 1.1.0

- Close QueryRunner statements on parameter-binding failures, preserving original
  and suppressed exceptions; queryOne no longer builds the entire result list.
- Add optional native Hikari JMX diagnostics for all three databases, default off,
  with tested pool lifecycle/reconfiguration behavior and configuration examples.
- Protect complex PostgreSQL psql scripts with real procedural/meta-command/COPY,
  replay, rollback/history, and transaction opt-out integration coverage.
- Add a binary Maven repository release ZIP, checksums, examples, and installation
  guide; package only this project's artifacts, without private cache contents.

- Add the optional `de.drremote.db:json` adapter and `json-features` Karaf feature.
  Query rows as JSON, bind JSON values, and explicitly parse JSON text columns
  inside existing transactions. Existing database cores retain their APIs and
  dependencies. Includes PostgreSQL/SQLite/DuckDB integration tests and a buildable
  JSON settings client.

- Add DuckDB as an embedded analytical database option, with a JDBC bundle, Karaf feature, configuration, and tests.
- Refresh PostgreSQL and SQLite transaction handling. Nested calls on the same transaction manager/thread join the outer transaction; a nested failure marks it rollback-only even if caught.
- Add SQLite atomic batch migrations, optional baselines for new installations, migration SQL drift detection, and automatic migration readiness services.
- Refresh PostgreSQL migration coordination, JDBC URL conversion, and `psql` execution. Script migrations default to a single transaction and stop on SQL errors.
- Fix PostgreSQL JDBC migration schema creation and run migration locking/work on one borrowed connection. Failed lock acquisition releases its connection.
- Add regression tests, live PostgreSQL CI, Java 17/21 builds, actual Karaf feature resolution checks, and a buildable SQLite client example.
- Correct the source-build and installation instructions. The former public Maven repository is retired; project artifacts must be built locally.

### Upgrading from the existing public source

The public Maven names remain `de.drremote.db:postgresql`, `sqlite`, and their `*-features` artifacts. Exported Java API packages and bundle symbolic names remain unchanged. DuckDB adds `de.drremote.db:duckdb` and `duckdb-features`.

Use `1.1.0` consistently for Maven artifacts; Karaf displays its normalized feature/bundle version as `1.1.0`. The former README's `1.0.0-SNAPSHOT` examples did not match the public POM's `1.0.0` version.

The minimum Java version is now 17. The supported feature target is Karaf 4.4.11. Existing PostgreSQL/SQLite single-statement migration descriptors remain supported; the new SQLite baseline method has a default implementation returning `null`.

Nested PostgreSQL/SQLite transaction failures now prevent an outer commit. PostgreSQL script migrations default to `psql --single-transaction`; override `ScriptMigration.isSingleTransaction()` only for commands that cannot execute inside a transaction. Review both changes if older callers relied on the previous behavior.

Do not edit already-applied migrations. Add a new version instead. SQLite now reports SQL drift for recorded migrations with stored SQL. Baselines only apply when the plugin has no existing migration history.

DuckDB currently has a simpler transaction/migration layer: avoid nested transaction calls, register/apply migrations explicitly, and use one SQL statement per migration. It does not expose the PostgreSQL/SQLite `DatabaseReady` contract.

Versioned binary release archives contain the public project artifacts and examples; project artifacts are not published to Maven Central.
