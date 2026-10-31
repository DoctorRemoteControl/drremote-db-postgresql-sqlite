# DrRemote DB

[![Build](https://github.com/DoctorRemoteControl/drremote-db-postgresql-sqlite/actions/workflows/build.yml/badge.svg)](https://github.com/DoctorRemoteControl/drremote-db-postgresql-sqlite/actions/workflows/build.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

PostgreSQL, SQLite, and DuckDB services for modular Java applications running in **OSGi / Apache Karaf**. Each database bundle exposes a pooled JDBC data source, transactions, parameterized queries, and versioned plugin migrations through OSGi Declarative Services.

| Database | Typical use | Migration support |
| --- | --- | --- |
| PostgreSQL | Applications using a database server | Per-plugin schemas, JDBC and `psql` scripts, readiness services |
| SQLite | Embedded applications and local storage | Single statements, atomic batches, baselines, readiness services |
| DuckDB | Embedded analytical workloads | Explicit single-statement migrations |

**Current development version: `1.1.0-SNAPSHOT`.** Build this project from source. Its artifacts are not published to Maven Central, and the former public DrRemote Maven repository is no longer available. Third-party dependencies come from Maven Central; no private server or account is required.

## Requirements

- JDK **17 or later**; CI checks Java 17 and 21.
- Maven **3.9 or later**.
- Apache Karaf **4.4.11** for the supplied features, or an OSGi runtime with Declarative Services, ConfigAdmin, and the required JDBC/HikariCP bundles.
- PostgreSQL only: an existing database and database user. Install `psql` on the runtime host if you use script migrations.

SQLite and DuckDB use embedded native JDBC drivers. Native platform support follows the selected driver versions.

## Build

```sh
git clone https://github.com/DoctorRemoteControl/drremote-db-postgresql-sqlite.git
cd drremote-db-postgresql-sqlite
mvn clean install
```

This runs the tests, builds the bundles and Karaf feature XML, checks the features' OSGi dependency resolution, and installs the project artifacts into your **local** Maven repository (normally `~/.m2/repository`). It does not publish them to a server.

For validation without installation, use `mvn clean verify`. CI also uploads bundles, source/Javadoc JARs, feature descriptors, and test reports as workflow artifacts.

## Quick start: SQLite in Karaf

1. Build with `mvn clean install` as above.
2. Copy [the SQLite configuration](examples/de.drremote.db.sqlite.cfg.example) to your Karaf installation:

   ```sh
   cp examples/de.drremote.db.sqlite.cfg.example "$KARAF_HOME/etc/de.drremote.db.sqlite.cfg"
   ```

3. In the Karaf console, install the feature:

   ```text
   feature:repo-add mvn:de.drremote.db/sqlite-features/1.1.0-SNAPSHOT/xml/features
   feature:install de.drremote-db-sqlite
   service:list de.drremote.db.sqlite.api.DbCore
   ```

The example database is stored at `${karaf.data}/drremote.sqlite`. Karaf must run as the same user as Maven, or have its Maven resolver's local repository configured to the repository used for `mvn install`. See [Karaf installation and troubleshooting](docs/installation.md).

### Try a client bundle

The [notes example](examples/notes-client) contains a real migration descriptor and repository service:

```sh
mvn -f examples/notes-client/pom.xml clean package
```

Then install its JAR in the Karaf console, replacing the path with your checkout's absolute path:

```text
bundle:install -s file:/absolute/path/to/drremote-db-postgresql-sqlite/examples/notes-client/target/notes-client-1.1.0-SNAPSHOT.jar
service:list example.notes.NotesRepository
```

The migration registry creates the `notes` table and publishes a `DatabaseReady` service. The repository waits for that service before activation. Read the [SQLite usage guide](docs/usage-sqlite.md) for queries, transaction behavior, batches, and baselines.

## Other databases

| Database | Example configuration | Karaf feature repository | Feature |
| --- | --- | --- | --- |
| PostgreSQL | [Configuration](examples/de.drremote.db.postgresql.cfg.example) | `mvn:de.drremote.db/postgresql-features/1.1.0-SNAPSHOT/xml/features` | `de.drremote-db-postgresql` |
| DuckDB | [Configuration](examples/de.drremote.db.duckdb.cfg.example) | `mvn:de.drremote.db/duckdb-features/1.1.0-SNAPSHOT/xml/features` | `de.drremote-db-duckdb` |

Copy the appropriate configuration to `$KARAF_HOME/etc/`, removing the `.example` suffix, and replace PostgreSQL placeholders before installing its feature.

## Maven coordinates

All entries below use version `1.1.0-SNAPSHOT`, after building and installing from source:

| Group | Bundle artifact | Feature artifact | Java API package |
| --- | --- | --- | --- |
| `de.drremote.db` | `postgresql` | `postgresql-features` | `de.drremote.db.postgresql.api` |
| `de.drremote.db` | `sqlite` | `sqlite-features` | `de.drremote.db.sqlite.api` |
| `de.drremote.db` | `duckdb` | `duckdb-features` | `de.drremote.db.duckdb.api` |

Use a `provided` dependency in an OSGi client; the runtime installs the database bundle and its dependencies. [The example POM](examples/notes-client/pom.xml) shows a complete client build. These libraries are OSGi services, rather than standalone database servers or a command-line application.

## Documentation

- [Installation and troubleshooting](docs/installation.md)
- PostgreSQL: [configuration](docs/configuration-postgresql.md) · [usage](docs/usage-postgresql.md)
- SQLite: [configuration](docs/configuration-sqlite.md) · [usage](docs/usage-sqlite.md)
- DuckDB: [configuration](docs/configuration-duckdb.md) · [usage](docs/usage-duckdb.md)
- [Changes and upgrade notes](CHANGELOG.md)
- [Contributing and validation](CONTRIBUTING.md)

The project is in active development. Test upgrades against your own schema and workloads before using them with existing data. Licensed under [Apache-2.0](LICENSE).
