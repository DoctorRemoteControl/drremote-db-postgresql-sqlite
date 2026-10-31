# DrRemote DB — SQLite

OSGi database services for SQLite: pooled JDBC access, transactions, queries, and plugin migrations.

- Public artifact: `de.drremote.db:sqlite:1.1.0-SNAPSHOT`
- API package / bundle symbolic name: `de.drremote.db.sqlite.api` / `de.drremote.db.sqlite`
- ConfigAdmin PID: `de.drremote.db.sqlite`
- Java 17+; supplied Karaf feature targets 4.4.11.

Build from the repository root with `mvn clean install`. Project artifacts must currently be built locally; there is no hosted public DrRemote Maven repository.

See the [main README](../README.md), [configuration](../docs/configuration-sqlite.md), [usage](../docs/usage-sqlite.md), and [installation guide](../docs/installation.md).

Licensed under [Apache-2.0](../LICENSE).
