# DrRemote DB — DuckDB

OSGi database services for DuckDB: pooled JDBC access, transactions, queries, and plugin migrations.

- Public artifact: `de.drremote.db:duckdb:1.1.0-SNAPSHOT`
- API package / bundle symbolic name: `de.drremote.db.duckdb.api` / `de.drremote.db.duckdb`
- ConfigAdmin PID: `de.drremote.db.duckdb`
- Java 17+; supplied Karaf feature targets 4.4.11.

Build from the repository root with `mvn clean install`. Project artifacts must currently be built locally; there is no hosted public DrRemote Maven repository.

See the [main README](../README.md), [configuration](../docs/configuration-duckdb.md), [usage](../docs/usage-duckdb.md), and [installation guide](../docs/installation.md).

Licensed under [Apache-2.0](../LICENSE).
