# DrRemote DB

`DrRemote DB` provides the PostgreSQL database layer for DrRemote as a modular OSGi/Karaf repository.

It contains:

* a PostgreSQL core bundle for JDBC access, transactions, and migrations
* a Karaf features module for simple runtime installation

## Overview

This repository is intended for modular Java applications running in OSGi/Karaf that need a shared PostgreSQL integration layer.

The PostgreSQL core provides:

* JDBC-based database access
* transaction handling via `TransactionManager`
* a HikariCP-backed `DataSource`
* lightweight query helpers
* schema-based plugin migrations
* centralized migration tracking in `core.schema_migrations`

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

## Requirements

* Java 21
* Apache Karaf or another OSGi runtime
* PostgreSQL
* PostgreSQL JDBC driver available in the runtime
* HikariCP available in the runtime
* `psql` available on `PATH` for script-based migrations

## Build

Build the complete repository with:

```sh
mvn clean install
```

## Artifacts

Published artifacts:

* Bundle: `de.drremote.db:postgresql`
* Feature: `de.drremote.db:postgresql-features`

Karaf feature name:

* `de.drremote-db-postgresql`

## Karaf Usage

Add the features repository:

```sh
feature:repo-add mvn:de.drremote.db/postgresql-features/1.0.0/xml/features
```

Install the feature:

```sh
feature:install de.drremote-db-postgresql
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

* `docs/configuration.md`

## Documentation

Repository documentation:

* `docs/configuration.md` — configuration examples
* `docs/usage.md` — usage examples
* `de.drremote.db.postgresql/README.md` — module-level documentation

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
└─ de.drremote.db.postgresql.features/
```

## Notes

* Configuration examples use placeholder credentials.
* Do not use example passwords unchanged in production.
* This repository is functional, but not yet production-hardened.

## License

Licensed under Apache-2.0. See `LICENSE`.
