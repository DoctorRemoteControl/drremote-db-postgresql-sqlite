# DrRemote DB

`DrRemote DB` provides PostgreSQL and SQLite database layers for DrRemote as a modular OSGi/Karaf repository.

It contains:

* a PostgreSQL core bundle for JDBC access, transactions, and migrations
* a SQLite core bundle for file-based JDBC access, transactions, and migrations
* Karaf features modules for simple runtime installation

## Overview

This repository is intended for modular Java applications running in OSGi/Karaf that need reusable database integration layers for PostgreSQL or SQLite.

The database cores provide:

* JDBC-based database access
* transaction handling via `TransactionManager`
* a HikariCP-backed `DataSource`
* lightweight query helpers
* plugin migrations
* centralized migration tracking

## Modules

### `de.drremote.db.postgresql`

The PostgreSQL core bundle.

Provides:

* PostgreSQL connectivity
* pooled `DataSource`
* transaction support
* query execution helpers
* plugin migration support

### `de.drremote.db.postgresql.features`

The Karaf features module.

Provides:

* a Karaf feature descriptor
* runtime provisioning of:

  * PostgreSQL JDBC driver
  * HikariCP
  * the DrRemote PostgreSQL core bundle

### `de.drremote.db.sqlite`

The SQLite core bundle.

Provides:

* SQLite connectivity
* pooled `DataSource`
* transaction support
* query execution helpers
* plugin migration support

### `de.drremote.db.sqlite.features`

The Karaf features module.

Provides:

* a Karaf feature descriptor
* runtime provisioning of:

  * SQLite JDBC driver
  * HikariCP
  * the DrRemote SQLite core bundle

## Requirements

* Java 21
* Apache Karaf or another OSGi runtime
* PostgreSQL
* PostgreSQL JDBC driver available in the runtime
* HikariCP available in the runtime
* `psql` available on `PATH` for script-based migrations
* SQLite JDBC driver available in the runtime

## Build

Build the complete repository with:

```sh
mvn clean install
```

## Artifacts

Published artifacts:

* Bundle: `de.drremote.db:postgresql`
* Feature: `de.drremote.db:postgresql-features`
* Bundle: `de.drremote.db:sqlite`
* Feature: `de.drremote.db:sqlite-features`

Karaf feature name:

* `de.drremote-db-postgresql`
* `de.drremote-db-sqlite`

## Karaf Usage

Add the features repository:

```sh
feature:repo-add mvn:de.drremote.db/postgresql-features/1.0.0/xml/features
```

Install the feature:

```sh
feature:install de.drremote-db-postgresql
```

SQLite:

Add the features repository:

```sh
feature:repo-add mvn:de.drremote.db/sqlite-features/1.0.0/xml/features
```

Install the feature:

```sh
feature:install de.drremote-db-sqlite
```

## Configuration

The PostgreSQL core is configured through the ConfigAdmin PID:

```text
de.drremote.db.postgresql
```

Recommended configuration file:

```text
$KARAF_HOME/etc/de.drremote.db.postgresql.cfg
```

For a full configuration example, see:

* `docs/configuration-postgresql.md`

The SQLite core is configured through the ConfigAdmin PID:

```text
de.drremote.db.sqlite
```

Recommended configuration file:

```text
$KARAF_HOME/etc/de.drremote.db.sqlite.cfg
```

## Documentation

Repository documentation:

* `docs/configuration-postgresql.md` — PostgreSQL configuration
* `docs/configuration-sqlite.md` — SQLite configuration
* `docs/usage-postgresql.md` — PostgreSQL usage examples
* `docs/usage-sqlite.md` — SQLite usage examples
* `de.drremote.db.postgresql/README.md` — module-level documentation
* `de.drremote.db.sqlite/README.md` — module-level documentation

## Repository Layout

```text
db/
├─ pom.xml
├─ README.md
├─ LICENSE
├─ docs/
│  ├─ configuration.md
│  └─ usage.md
├─ de.drremote.db.postgresql/
├─ de.drremote.db.postgresql.features/
├─ de.drremote.db.sqlite/
└─ de.drremote.db.sqlite.features/
```

## Notes

* Configuration examples use placeholder credentials.
* Do not use example passwords unchanged in production.
* This repository is functional, but not yet production-hardened.

## License

Licensed under Apache-2.0. See `LICENSE`.
