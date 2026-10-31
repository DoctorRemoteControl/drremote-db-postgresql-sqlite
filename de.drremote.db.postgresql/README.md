# DrRemote DB — PostgreSQL

OSGi database services for PostgreSQL: pooled JDBC access, transactions, queries, and plugin migrations.

- Public artifact: `de.drremote.db:postgresql:1.1.0-SNAPSHOT`
- API package / bundle symbolic name: `de.drremote.db.postgresql.api` / `de.drremote.db.postgresql`
- ConfigAdmin PID: `de.drremote.db.postgresql`
- Java 17+; supplied Karaf feature targets 4.4.11.

Build from the repository root with `mvn clean install`. Project artifacts must currently be built locally; there is no hosted public DrRemote Maven repository.

See the [main README](../README.md), [configuration](../docs/configuration-postgresql.md), [usage](../docs/usage-postgresql.md), and [installation guide](../docs/installation.md).

Licensed under [Apache-2.0](../LICENSE).
