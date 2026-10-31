# Changelog

## 1.1.0-SNAPSHOT — unreleased

- Add DuckDB as an embedded analytical database option, with a JDBC bundle, Karaf feature, configuration, and tests.
- Refresh PostgreSQL and SQLite transaction handling. Nested calls on the same transaction manager/thread join the outer transaction; a nested failure marks it rollback-only even if caught.
- Add SQLite atomic batch migrations, optional baselines for new installations, migration SQL drift detection, and automatic migration readiness services.
- Refresh PostgreSQL migration coordination, JDBC URL conversion, and `psql` execution. Script migrations default to a single transaction and stop on SQL errors.
- Add regression tests, Java 17/21 CI, actual Karaf feature resolution checks, and a buildable SQLite client example.
- Correct the source-build and installation instructions. The former public Maven repository is retired; project artifacts must be built locally.

### Upgrading from the existing public source

The public Maven names remain `de.drremote.db:postgresql`, `sqlite`, and their `*-features` artifacts. Exported Java API packages and bundle symbolic names remain unchanged. DuckDB adds `de.drremote.db:duckdb` and `duckdb-features`.

Use `1.1.0-SNAPSHOT` consistently for Maven artifacts; Karaf displays its normalized feature/bundle version as `1.1.0.SNAPSHOT`. The former README's `1.0.0-SNAPSHOT` examples did not match the public POM's `1.0.0` version.

The minimum Java version is now 17. The supported feature target is Karaf 4.4.11. Existing PostgreSQL/SQLite single-statement migration descriptors remain supported; the new SQLite baseline method has a default implementation returning `null`.

Nested PostgreSQL/SQLite transaction failures now prevent an outer commit. PostgreSQL script migrations default to `psql --single-transaction`; override `ScriptMigration.isSingleTransaction()` only for commands that cannot execute inside a transaction. Review both changes if older callers relied on the previous behavior.

Do not edit already-applied migrations. Add a new version instead. SQLite now reports SQL drift for recorded migrations with stored SQL. Baselines only apply when the plugin has no existing migration history.

DuckDB currently has a simpler transaction/migration layer: avoid nested transaction calls, register/apply migrations explicitly, and use one SQL statement per migration. It does not expose the PostgreSQL/SQLite `DatabaseReady` contract.

There is no new tagged release or remotely published artifact implied by this development version.
