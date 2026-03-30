# de.drremote.db.postgresql

`de.drremote.db.postgresql` is an OSGi/Karaf bundle that provides PostgreSQL access, transaction handling, and schema-based plugin migrations for modular applications.

It is built for setups where multiple plugins share the same PostgreSQL database while keeping their own schema and migration history.

## Features

* OSGi Declarative Services (DS)
* PostgreSQL access via JDBC
* HikariCP-backed `DataSource`
* Transaction handling through `TransactionManager`
* Lightweight query API via `QueryRunner`
* Plugin-specific schema switching via PostgreSQL `search_path`
* Central migration tracking in `core.schema_migrations`
* Configuration through Karaf `ConfigAdmin`

## Requirements

* Java 21
* Apache Karaf or another OSGi runtime
* PostgreSQL
* PostgreSQL JDBC driver available in the runtime
* HikariCP available in the runtime
* `psql` installed and available on `PATH` for script-based migrations

## Build

```sh
mvn clean install
```

## Configuration

Configuration PID:

```text
de.drremote.db.postgresql
```

Recommended Karaf config file:

```text
$KARAF_HOME/etc/de.drremote.db.postgresql.cfg
```

Example:

```properties
#############################################
# DB.POSTGRESQL Config (PID: de.drremote.db.postgresql)
#############################################

# --- JDBC / PostgreSQL ---
jdbcUrl=jdbc:postgresql://localhost:5432/appdb
username=karaf_postgresql
password=change_me

# --- HikariCP Pool ---
poolName=db-postgresql-pool
maxPoolSize=10
minIdle=1

#############################################
# Optional: legacy keys (deprecated)
# (only used if the "new" keys above are NOT set)
#############################################
# dbcore.jdbcUrl=jdbc:postgresql://localhost:5432/appdb
# dbcore.username=karaf_postgresql
# dbcore.password=change_me
```

Alternatively, configure it through the Karaf shell:

```sh
config:edit de.drremote.db.postgresql

config:property-set jdbcUrl jdbc:postgresql://localhost:5432/appdb
config:property-set username karaf_postgresql
config:property-set password change_me
config:property-set poolName db-postgresql-pool
config:property-set maxPoolSize 10
config:property-set minIdle 1

config:update
```

Verify the active configuration:

```sh
config:list "(service.pid=de.drremote.db.postgresql)"

config:edit de.drremote.db.postgresql
config:property-list
```

## Quick Start

Inject `TransactionManager` and execute your database code inside a transaction:

```java
@Component
public class ProjectRepository {

    @Reference
    private TransactionManager tx;

    public List<String> findAllNames() {
        return tx.inTransaction(handle -> {
            handle.setSearchPath("sqlcode");
            return handle.getQueryRunner().query(
                "SELECT name FROM projects ORDER BY name",
                rs -> rs.getString(1)
            );
        });
    }
}
```

Write operations work the same way:

```java
@Component
public class ProjectWriter {

    @Reference
    private TransactionManager tx;

    public void insert(String name) {
        tx.inTransaction(handle -> {
            handle.setSearchPath("sqlcode");
            handle.getQueryRunner().update(
                "INSERT INTO projects(name) VALUES (?)",
                name
            );
            return null;
        });
    }
}
```

## Main APIs

### `DbCore`

Marker service for the PostgreSQL core lifecycle.

Referencing this service guarantees that the PostgreSQL core is active.

### `TransactionManager`

Executes code inside a JDBC transaction.

### `DbHandle`

Provides access to:

* the current JDBC connection
* the `QueryRunner`
* PostgreSQL `search_path` handling

### `QueryRunner`

Simple helper API for:

* `query(...)`
* `queryOne(...)`
* `update(...)`

### `MigrationService`

Executes core and plugin migrations and stores applied versions centrally.

### `ScriptMigration`

Migration type backed by external SQL scripts executed through `psql`.

> The current implementation supports `ScriptMigration` only.

## Database Preparation

A preparation script is included at:

```text
src/main/resources/migrations/init.sql
```

The script can:

* create a database role
* create a database
* create the `core` schema
* create the `core.schema_migrations` table
* grant base and default privileges

Run it as a PostgreSQL superuser:

```sh
psql -U postgres -f src/main/resources/migrations/init.sql
```

Before running it, adjust the variables in the script:

```sql
\set dbname 'appdb'
\set role   'karaf_postgresql'
\set role_password 'CHANGE_ME'
```

## Migrations

This module stores migration state in:

```text
core.schema_migrations
```

Tracked fields include:

* `plugin_name`
* `schema_name`
* `version`
* `description`
* `sql`
* `applied_at`

Primary key:

```text
(plugin_name, version)
```

Migrations are sorted by version and applied once.

Example plugin migration descriptor:

```java
public final class SqlcodeMigration implements PluginMigrationDescriptor {

    @Override
    public String getPluginName() {
        return "sqlcode";
    }

    @Override
    public String getSchemaName() {
        return "sqlcode";
    }

    @Override
    public List<Migration> getMigrations() {
        return List.of(new ScriptMigration() {
            @Override
            public int getVersion() {
                return 1;
            }

            @Override
            public String getDescription() {
                return "initial schema";
            }

            @Override
            public URL getScriptUrl() {
                return getClass().getResource("/migrations/sqlcode/V1__init.sql");
            }
        });
    }
}
```

Run the migration through `MigrationService`:

```java
@Component(immediate = true)
public class MigrationRunner {

    @Reference
    void run(MigrationService migrationService) {
        migrationService.migratePlugin(new SqlcodeMigration());
    }
}
```

## Implementation Notes

* Transactions are backed by JDBC connections from the registered `DataSource`
* On success, transactions are committed automatically
* On failure, transactions are rolled back automatically
* The implementation is PostgreSQL-specific
* Script migrations require `psql` and a reachable database host
* Schema names should be treated as trusted internal identifiers

## Project Structure

```text
src/main/java/de/drremote/db/postgresql/api
src/main/java/de/drremote/db/postgresql/impl
src/main/java/de/drremote/db/postgresql/impl/jdbc
src/main/java/de/drremote/db/postgresql/impl/migration
src/main/resources/migrations
```

## Roadmap

Planned or useful next improvements:

* structured logging instead of `System.out`
* stronger schema name validation
* more tests
* expanded documentation
* clearer separation between generic migrations and script migrations

## License

Licensed under Apache-2.0. See `LICENSE`.
