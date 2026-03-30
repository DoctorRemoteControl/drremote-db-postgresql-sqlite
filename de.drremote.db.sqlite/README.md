# de.drremote.db.sqlite

`de.drremote.db.sqlite` is an OSGi/Karaf bundle that provides SQLite access, transaction handling, and lightweight plugin migrations for modular applications.

It is intended for smaller or embedded deployments where a local file-based database is preferred over PostgreSQL.

## Features

- OSGi Declarative Services (DS)
- SQLite access via JDBC
- HikariCP-backed `DataSource`
- Transaction handling through `TransactionManager`
- Lightweight query API via `QueryRunner`
- Plugin migrations tracked in `core_schema_migrations`
- Configuration through Karaf `ConfigAdmin`

## Requirements

- Java 21
- Apache Karaf or another OSGi runtime
- SQLite JDBC driver available in the runtime
- HikariCP available in the runtime

## Build

```sh
mvn clean install
```

## Configuration

Configuration PID:

```text
de.drremote.db.sqlite
```

Recommended Karaf config file:

```text
$KARAF_HOME/etc/de.drremote.db.sqlite.cfg
```

Example:

```properties
jdbcUrl=jdbc:sqlite:${karaf.data}/drremote.db
poolName=db-sqlite-pool
maxPoolSize=1
minIdle=1
busyTimeoutMs=5000
foreignKeys=true
```

## Notes

SQLite does not provide PostgreSQL-style schemas or `search_path`.

For plugin isolation, use clear table prefixes such as:

* `sqlcode_projects`
* `iam_users`
* `shell_commands`

Migration state is stored in:

```text
core_schema_migrations
```

For SQLite, keep one migration limited to one SQL statement unless a dedicated script runner is introduced.

## Artifacts

* Bundle: `de.drremote.db:sqlite`
* Feature: `de.drremote.db:sqlite-features`
* Karaf feature name: `de.drremote-db-sqlite`

## Using In Karaf

```text
feature:repo-add mvn:de.drremote.db/sqlite-features/1.0.0/xml/features
feature:install de.drremote-db-sqlite
```

## License

Licensed under Apache-2.0. See `LICENSE`.
